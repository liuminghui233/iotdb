/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.db.query.udf.core.layer;

import org.apache.iotdb.db.exception.query.QueryProcessException;
import org.apache.iotdb.db.query.expression.Expression;
import org.apache.iotdb.db.query.udf.api.access.Row;
import org.apache.iotdb.db.query.udf.api.access.RowWindow;
import org.apache.iotdb.db.query.udf.api.customizer.strategy.SlidingSizeWindowAccessStrategy;
import org.apache.iotdb.db.query.udf.api.customizer.strategy.SlidingTimeWindowAccessStrategy;
import org.apache.iotdb.db.query.udf.core.access.ElasticSerializableTVListBackedSingleColumnRow;
import org.apache.iotdb.db.query.udf.core.access.ElasticSerializableTVListBackedSingleColumnWindow;
import org.apache.iotdb.db.query.udf.core.layer.SafetyLine.SafetyPile;
import org.apache.iotdb.db.query.udf.core.reader.LayerPointReader;
import org.apache.iotdb.db.query.udf.core.reader.LayerRowReader;
import org.apache.iotdb.db.query.udf.core.reader.LayerRowWindowReader;
import org.apache.iotdb.db.query.udf.datastructure.tv.ElasticSerializableTVList;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.utils.Binary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class SingleInputColumnMultiReferenceIntermediateLayer extends IntermediateLayer {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(SingleInputColumnMultiReferenceIntermediateLayer.class);

  private final LayerPointReader parentLayerPointReader;
  private final TSDataType parentLayerPointReaderDataType;
  private final boolean isParentLayerPointReaderConstant;
  private final ElasticSerializableTVList tvList;
  private final SafetyLine safetyLine;

  public SingleInputColumnMultiReferenceIntermediateLayer(
      Expression expression,
      long queryId,
      float memoryBudgetInMB,
      LayerPointReader parentLayerPointReader)
      throws QueryProcessException {
    super(expression, queryId, memoryBudgetInMB);
    this.parentLayerPointReader = parentLayerPointReader;

    parentLayerPointReaderDataType = parentLayerPointReader.getDataType();
    isParentLayerPointReaderConstant = parentLayerPointReader.isConstantPointReader();
    tvList =
        ElasticSerializableTVList.newElasticSerializableTVList(
            parentLayerPointReaderDataType, queryId, memoryBudgetInMB, CACHE_BLOCK_SIZE);
    safetyLine = new SafetyLine();
  }

  @Override
  public LayerPointReader constructPointReader() {

    return new LayerPointReader() {

      private final SafetyPile safetyPile = safetyLine.addSafetyPile();

      private boolean hasCached = false;
      private int currentPointIndex = -1;

      @Override
      public boolean isConstantPointReader() {
        return isParentLayerPointReaderConstant;
      }

      @Override
      public boolean next() throws QueryProcessException, IOException {
        if (!hasCached
            && (currentPointIndex < tvList.size() - 1
                || LayerCacheUtils.cachePoint(
                    parentLayerPointReaderDataType, parentLayerPointReader, tvList))) {
          ++currentPointIndex;
          hasCached = true;
        }

        return hasCached;
      }

      @Override
      public void readyForNext() {
        hasCached = false;

        safetyPile.moveForwardTo(currentPointIndex + 1);
        tvList.setEvictionUpperBound(safetyLine.getSafetyLine());
      }

      @Override
      public TSDataType getDataType() {
        return parentLayerPointReaderDataType;
      }

      @Override
      public long currentTime() throws IOException {
        return tvList.getTime(currentPointIndex);
      }

      @Override
      public int currentInt() throws IOException {
        return tvList.getInt(currentPointIndex);
      }

      @Override
      public long currentLong() throws IOException {
        return tvList.getLong(currentPointIndex);
      }

      @Override
      public float currentFloat() throws IOException {
        return tvList.getFloat(currentPointIndex);
      }

      @Override
      public double currentDouble() throws IOException {
        return tvList.getDouble(currentPointIndex);
      }

      @Override
      public boolean currentBoolean() throws IOException {
        return tvList.getBoolean(currentPointIndex);
      }

      @Override
      public Binary currentBinary() throws IOException {
        return tvList.getBinary(currentPointIndex);
      }

      @Override
      public boolean isCurrentNull() throws IOException {
        return tvList.isNull(currentPointIndex);
      }
    };
  }

  @Override
  public LayerRowReader constructRowReader() {

    return new LayerRowReader() {

      private final SafetyPile safetyPile = safetyLine.addSafetyPile();
      private final ElasticSerializableTVListBackedSingleColumnRow row =
          new ElasticSerializableTVListBackedSingleColumnRow(tvList, -1);

      private boolean hasCached = false;
      private int currentRowIndex = -1;

      @Override
      public boolean next() throws QueryProcessException, IOException {
        if (!hasCached
            && ((currentRowIndex < tvList.size() - 1)
                || LayerCacheUtils.cachePoint(
                    parentLayerPointReaderDataType, parentLayerPointReader, tvList))) {
          row.seek(++currentRowIndex);
          hasCached = true;
        }

        return hasCached;
      }

      @Override
      public void readyForNext() {
        hasCached = false;

        safetyPile.moveForwardTo(currentRowIndex + 1);
        tvList.setEvictionUpperBound(safetyLine.getSafetyLine());
      }

      @Override
      public TSDataType[] getDataTypes() {
        return new TSDataType[] {parentLayerPointReaderDataType};
      }

      @Override
      public long currentTime() throws IOException {
        return row.getTime();
      }

      @Override
      public Row currentRow() {
        return row;
      }

      @Override
      public boolean isCurrentNull() throws IOException {
        return tvList.isNull(currentRowIndex);
      }
    };
  }

  @Override
  protected LayerRowWindowReader constructRowSlidingSizeWindowReader(
      SlidingSizeWindowAccessStrategy strategy, float memoryBudgetInMB) {

    return new LayerRowWindowReader() {

      private final int windowSize = strategy.getWindowSize();
      private final int slidingStep = strategy.getSlidingStep();

      private final SafetyPile safetyPile = safetyLine.addSafetyPile();
      private final ElasticSerializableTVListBackedSingleColumnWindow window =
          new ElasticSerializableTVListBackedSingleColumnWindow(tvList);

      private boolean hasCached = false;
      private int beginIndex = -slidingStep;

      @Override
      public boolean next() throws IOException, QueryProcessException {
        if (hasCached) {
          return true;
        }

        beginIndex += slidingStep;
        int endIndex = beginIndex + windowSize;
        if (beginIndex < 0 || endIndex < 0) {
          LOGGER.warn(
              "SingleInputColumnMultiReferenceIntermediateLayer$LayerRowWindowReader: index overflow. beginIndex: {}, endIndex: {}, windowSize: {}.",
              beginIndex,
              endIndex,
              windowSize);
          return false;
        }

        int pointsToBeCollected = endIndex - tvList.size();
        if (0 < pointsToBeCollected) {
          LayerCacheUtils.cachePoints(
              parentLayerPointReaderDataType, parentLayerPointReader, tvList, pointsToBeCollected);
          if (tvList.size() <= beginIndex) {
            return false;
          }

          window.seek(
              beginIndex,
              tvList.size(),
              tvList.getTime(beginIndex),
              tvList.getTime(tvList.size() - 1));
        } else {
          window.seek(
              beginIndex, endIndex, tvList.getTime(beginIndex), tvList.getTime(endIndex - 1));
        }

        hasCached = true;
        return true;
      }

      @Override
      public void readyForNext() {
        hasCached = false;

        safetyPile.moveForwardTo(beginIndex + 1);
        tvList.setEvictionUpperBound(safetyLine.getSafetyLine());
      }

      @Override
      public TSDataType[] getDataTypes() {
        return new TSDataType[] {parentLayerPointReaderDataType};
      }

      @Override
      public RowWindow currentWindow() {
        return window;
      }
    };
  }

  @Override
  protected LayerRowWindowReader constructRowSlidingTimeWindowReader(
      SlidingTimeWindowAccessStrategy strategy, float memoryBudgetInMB)
      throws IOException, QueryProcessException {

    final long timeInterval = strategy.getTimeInterval();
    final long slidingStep = strategy.getSlidingStep();
    final long displayWindowEnd = strategy.getDisplayWindowEnd();

    final SafetyPile safetyPile = safetyLine.addSafetyPile();
    final ElasticSerializableTVListBackedSingleColumnWindow window =
        new ElasticSerializableTVListBackedSingleColumnWindow(tvList);

    long nextWindowTimeBeginGivenByStrategy = strategy.getDisplayWindowBegin();
    if (tvList.size() == 0
        && LayerCacheUtils.cachePoint(
            parentLayerPointReaderDataType, parentLayerPointReader, tvList)
        && nextWindowTimeBeginGivenByStrategy == Long.MIN_VALUE) {
      // display window begin should be set to the same as the min timestamp of the query result
      // set
      nextWindowTimeBeginGivenByStrategy = tvList.getTime(0);
    }
    long finalNextWindowTimeBeginGivenByStrategy = nextWindowTimeBeginGivenByStrategy;

    final boolean hasAtLeastOneRow = tvList.size() != 0;

    return new LayerRowWindowReader() {

      private boolean hasCached = false;
      private long nextWindowTimeBegin = finalNextWindowTimeBeginGivenByStrategy;
      private int nextIndexBegin = 0;

      @Override
      public boolean next() throws IOException, QueryProcessException {
        if (hasCached) {
          return true;
        }
        if (!hasAtLeastOneRow || displayWindowEnd <= nextWindowTimeBegin) {
          return false;
        }

        long nextWindowTimeEnd = Math.min(nextWindowTimeBegin + timeInterval, displayWindowEnd);
        while (tvList.getTime(tvList.size() - 1) < nextWindowTimeEnd) {
          if (!LayerCacheUtils.cachePoint(
              parentLayerPointReaderDataType, parentLayerPointReader, tvList)) {
            break;
          }
        }

        for (int i = nextIndexBegin; i < tvList.size(); ++i) {
          if (nextWindowTimeBegin <= tvList.getTime(i)) {
            nextIndexBegin = i;
            break;
          }
          if (i == tvList.size() - 1) {
            nextIndexBegin = tvList.size();
          }
        }

        int nextIndexEnd = tvList.size();
        for (int i = nextIndexBegin; i < tvList.size(); ++i) {
          if (nextWindowTimeEnd <= tvList.getTime(i)) {
            nextIndexEnd = i;
            break;
          }
        }
        window.seek(
            nextIndexBegin,
            nextIndexEnd,
            nextWindowTimeBegin,
            nextWindowTimeBegin + timeInterval - 1);

        hasCached = nextIndexBegin != nextIndexEnd;
        return hasCached;
      }

      @Override
      public void readyForNext() {
        hasCached = false;
        nextWindowTimeBegin += slidingStep;

        safetyPile.moveForwardTo(nextIndexBegin + 1);
        tvList.setEvictionUpperBound(safetyLine.getSafetyLine());
      }

      @Override
      public TSDataType[] getDataTypes() {
        return new TSDataType[] {parentLayerPointReaderDataType};
      }

      @Override
      public RowWindow currentWindow() {
        return window;
      }
    };
  }
}
