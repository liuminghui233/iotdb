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
package org.apache.iotdb.db.query.control;

import org.apache.iotdb.db.conf.IoTDBConfig;
import org.apache.iotdb.db.conf.IoTDBConstant;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.engine.StorageEngine;
import org.apache.iotdb.db.engine.querycontext.QueryDataSource;
import org.apache.iotdb.db.engine.storagegroup.TsFileResource;
import org.apache.iotdb.db.exception.StorageEngineException;
import org.apache.iotdb.db.exception.query.QueryProcessException;
import org.apache.iotdb.db.metadata.PartialPath;
import org.apache.iotdb.db.query.context.QueryContext;
import org.apache.iotdb.db.query.externalsort.serialize.IExternalSortFileDeserializer;
import org.apache.iotdb.db.query.udf.service.TemporaryQueryDataFileService;
import org.apache.iotdb.tsfile.read.expression.impl.SingleSeriesExpression;
import org.apache.iotdb.tsfile.read.filter.basic.Filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * QueryResourceManager manages resource (file streams) used by each query job, and assign Ids to
 * the jobs. During the life cycle of a query, the following methods must be called in strict order:
 * 1. assignQueryId - get an Id for the new query. 2. getQueryDataSource - open files for the job or
 * reuse existing readers. 3. endQueryForGivenJob - release the resource used by this job.
 */
public class QueryResourceManager {

  private final AtomicLong queryIdAtom = new AtomicLong();
  private final QueryFileManager filePathsManager;
  private static final Logger logger = LoggerFactory.getLogger(QueryResourceManager.class);
  private IoTDBConfig CONFIG = IoTDBDescriptor.getInstance().getConfig();

  /**
   * Record temporary files used for external sorting.
   *
   * <p>Key: query job id. Value: temporary file list used for external sorting.
   */
  private final Map<Long, List<IExternalSortFileDeserializer>> externalSortFileMap;

  private final Map<Long, Map<String, QueryDataSource>> cachedQueryDataSource;

  private QueryResourceManager() {
    filePathsManager = new QueryFileManager();
    externalSortFileMap = new ConcurrentHashMap<>();
    cachedQueryDataSource = new HashMap<>();
  }

  public static QueryResourceManager getInstance() {
    return QueryTokenManagerHelper.INSTANCE;
  }

  /** Register a new query. When a query request is created firstly, this method must be invoked. */
  public long assignQueryId(boolean isDataQuery) {
    long queryId = queryIdAtom.incrementAndGet();
    if (isDataQuery) {
      filePathsManager.addQueryId(queryId);
    }
    return queryId;
  }

  /**
   * register temporary file generated by external sort for resource release.
   *
   * @param queryId query job id
   * @param deserializer deserializer of temporary file in external sort.
   */
  public void registerTempExternalSortFile(
      long queryId, IExternalSortFileDeserializer deserializer) {
    externalSortFileMap.computeIfAbsent(queryId, x -> new ArrayList<>()).add(deserializer);
  }

  public QueryDataSource getQueryDataSourceByPath(
      PartialPath selectedPath, QueryContext context, Filter filter)
      throws StorageEngineException, QueryProcessException {

    SingleSeriesExpression singleSeriesExpression =
        new SingleSeriesExpression(selectedPath, filter);
    QueryDataSource queryDataSource =
        StorageEngine.getInstance().query(singleSeriesExpression, context, filePathsManager);
    // calculate the distinct number of seq and unseq tsfiles
    if (CONFIG.isEnablePerformanceTracing()) {
      TracingManager.getInstance()
          .getTracingInfo(context.getQueryId())
          .addTsFileSet(queryDataSource.getSeqResources(), queryDataSource.getUnseqResources());
    }
    return queryDataSource;
  }

  public QueryDataSource getQueryDataSource(
      PartialPath selectedPath, QueryContext context, Filter filter)
      throws StorageEngineException, QueryProcessException {

    long queryId = context.getQueryId();
    String storageGroupPath = StorageEngine.getInstance().getStorageGroupPath(selectedPath);
    String deviceId = selectedPath.getDevice();

    QueryDataSource queryDataSource;
    if (cachedQueryDataSource.containsKey(queryId)
        && cachedQueryDataSource.get(queryId).containsKey(storageGroupPath)) {
      queryDataSource = cachedQueryDataSource.get(queryId).get(storageGroupPath);
    } else {
      SingleSeriesExpression singleSeriesExpression =
          new SingleSeriesExpression(selectedPath, filter);
      queryDataSource =
          StorageEngine.getInstance().getAllQueryDataSource(singleSeriesExpression, context);
      cachedQueryDataSource
          .computeIfAbsent(queryId, k -> new HashMap<>())
          .put(storageGroupPath, queryDataSource);
    }

    if (queryDataSource.getUnSeqFileOrderIndexes(deviceId) == null) {
      Integer[] orderIndexes = new Integer[queryDataSource.getUnseqResources().size() + 1];
      fillOrderIndexes(
          deviceId,
          queryDataSource.getUnseqResources(),
          queryDataSource.getUnclosedUnseqResource(),
          orderIndexes,
          context.isAscending());
      queryDataSource.setUnSeqFileOrderIndexes(deviceId, orderIndexes);
    }

    return queryDataSource;
  }

  public void clearCachedQueryDataSource(PartialPath path, QueryContext context)
      throws StorageEngineException {
    long queryId = context.getQueryId();
    String storageGroupPath = StorageEngine.getInstance().getStorageGroupPath(path);
    if (cachedQueryDataSource.containsKey(queryId)) {
      cachedQueryDataSource.get(queryId).remove(storageGroupPath);
    }
  }

  private void fillOrderIndexes(
      String deviceId,
      List<TsFileResource> unseqResources,
      TsFileResource unclosedUnseqResource,
      Integer[] orderIndexes,
      boolean ascending) {
    AtomicInteger index = new AtomicInteger();
    Map<Integer, Long> intToOrderTimeMap =
        unseqResources.stream()
            .collect(
                Collectors.toMap(
                    key -> index.getAndIncrement(),
                    resource -> resource.getOrderTime(deviceId, ascending)));
    if (unclosedUnseqResource != null) {
      intToOrderTimeMap.put(index.get(), unclosedUnseqResource.getOrderTime(deviceId, ascending));
    }
    index.set(0);
    intToOrderTimeMap.entrySet().stream()
        .sorted(
            (t1, t2) ->
                ascending
                    ? Long.compare(t1.getValue(), t2.getValue())
                    : Long.compare(t2.getValue(), t1.getValue()))
        .collect(Collectors.toList())
        .forEach(item -> orderIndexes[index.getAndIncrement()] = item.getKey());
  }

  /**
   * Whenever the jdbc request is closed normally or abnormally, this method must be invoked. All
   * query tokens created by this jdbc request must be cleared.
   */
  @SuppressWarnings("squid:S3776") // Suppress high Cognitive Complexity warning
  public void endQuery(long queryId) throws StorageEngineException {
    try {
      if (CONFIG.isEnablePerformanceTracing()
          && TracingManager.getInstance().getTracingInfo(queryId) != null) {
        TracingManager.getInstance().writeTracingInfo(queryId);
        TracingManager.getInstance().writeEndTime(queryId);
      }
    } catch (IOException e) {
      logger.error(
          "Error while writing performance info to {}, {}",
          CONFIG.getTracingDir() + File.separator + IoTDBConstant.TRACING_LOG,
          e.getMessage());
    }

    // close file stream of external sort files, and delete
    if (externalSortFileMap.get(queryId) != null) {
      for (IExternalSortFileDeserializer deserializer : externalSortFileMap.get(queryId)) {
        try {
          deserializer.close();
        } catch (IOException e) {
          throw new StorageEngineException(e);
        }
      }
      externalSortFileMap.remove(queryId);
    }

    // remove usage of opened file paths of current thread
    filePathsManager.removeUsedFilesForQuery(queryId);

    // close and delete UDF temp files
    TemporaryQueryDataFileService.getInstance().deregister(queryId);

    // remove query info in QueryTimeManager
    QueryTimeManager.getInstance().unRegisterQuery(queryId);

    // remove cached QueryDataSource
    cachedQueryDataSource.remove(queryId);
  }

  private static class QueryTokenManagerHelper {

    private static final QueryResourceManager INSTANCE = new QueryResourceManager();

    private QueryTokenManagerHelper() {}
  }
}
