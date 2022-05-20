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

package org.apache.iotdb.db.mpp.aggregation.slidingwindow;

import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.read.common.TimeRange;
import org.apache.iotdb.tsfile.read.common.block.TsBlock;
import org.apache.iotdb.tsfile.read.common.block.column.ColumnBuilder;

import java.util.Deque;

public abstract class SlidingWindowAggregator {

  // current aggregate window
  protected TimeRange curTimeRange;

  // cached AggregateResult of pre-aggregate windows
  protected Deque<TsBlock[]> deque;

  // output aggregate result
  protected TsBlock[] aggregateResult;

  public TSDataType[] getOutputType() {
    return null;
  }

  public void outputResult(ColumnBuilder[] columnBuilder) {}

  public void processTsBlocks(TsBlock[] inputTsBlocks) {}

  public void setTimeRange(TimeRange curTimeRange) {
    this.curTimeRange = curTimeRange;
    evictingExpiredValue();
  }

  /** evicting expired element in queue and reset expired aggregateResult */
  protected abstract void evictingExpiredValue();
}
