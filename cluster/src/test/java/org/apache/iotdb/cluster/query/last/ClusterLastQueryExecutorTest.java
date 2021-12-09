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

package org.apache.iotdb.cluster.query.last;

import org.apache.iotdb.cluster.common.TestUtils;
import org.apache.iotdb.cluster.query.BaseQueryTest;
import org.apache.iotdb.cluster.query.RemoteQueryContext;
import org.apache.iotdb.db.exception.StorageEngineException;
import org.apache.iotdb.db.exception.metadata.IllegalPathException;
import org.apache.iotdb.db.exception.query.QueryProcessException;
import org.apache.iotdb.db.metadata.PartialPath;
import org.apache.iotdb.db.qp.physical.crud.LastQueryPlan;
import org.apache.iotdb.db.query.context.QueryContext;
import org.apache.iotdb.db.query.control.QueryResourceManager;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.read.common.Field;
import org.apache.iotdb.tsfile.read.common.RowRecord;
import org.apache.iotdb.tsfile.read.expression.IExpression;
import org.apache.iotdb.tsfile.read.expression.impl.GlobalTimeExpression;
import org.apache.iotdb.tsfile.read.filter.TimeFilter;
import org.apache.iotdb.tsfile.read.query.dataset.QueryDataSet;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClusterLastQueryExecutorTest extends BaseQueryTest {

  @Test
  public void testLastQueryTimeFilter()
      throws QueryProcessException, StorageEngineException, IOException, IllegalPathException {
    LastQueryPlan plan = new LastQueryPlan();
    plan.setDeduplicatedPaths(
        Collections.singletonList(new PartialPath(TestUtils.getTestSeries(0, 10))));
    plan.setDeduplicatedDataTypes(Collections.singletonList(TSDataType.DOUBLE));
    plan.setPaths(plan.getDeduplicatedPaths());
    plan.setDataTypes(plan.getDeduplicatedDataTypes());
    IExpression expression = new GlobalTimeExpression(TimeFilter.gtEq(Long.MAX_VALUE));
    plan.setExpression(expression);
    QueryContext context =
        new RemoteQueryContext(QueryResourceManager.getInstance().assignQueryId(true));

    try {
      ClusterLastQueryExecutor lastQueryExecutor =
          new ClusterLastQueryExecutor(plan, testMetaMember);
      QueryDataSet queryDataSet = lastQueryExecutor.execute(context, plan);
      assertFalse(queryDataSet.hasNext());
    } finally {
      QueryResourceManager.getInstance().endQuery(context.getQueryId());
    }
  }

  @Test
  public void testLastQueryNoTimeFilter()
      throws QueryProcessException, StorageEngineException, IOException, IllegalPathException {
    LastQueryPlan plan = new LastQueryPlan();
    plan.setDeduplicatedPaths(
        Collections.singletonList(new PartialPath(TestUtils.getTestSeries(0, 10))));
    plan.setDeduplicatedDataTypes(Collections.singletonList(TSDataType.DOUBLE));
    plan.setPaths(plan.getDeduplicatedPaths());
    plan.setDataTypes(plan.getDeduplicatedDataTypes());
    QueryContext context =
        new RemoteQueryContext(QueryResourceManager.getInstance().assignQueryId(true));

    try {
      ClusterLastQueryExecutor lastQueryExecutor =
          new ClusterLastQueryExecutor(plan, testMetaMember);
      QueryDataSet queryDataSet = lastQueryExecutor.execute(context, plan);
      assertTrue(queryDataSet.hasNext());
      RowRecord record = queryDataSet.next();
      List<Field> fields = record.getFields();
      Assert.assertEquals(
          (double) 10.0, Double.parseDouble(fields.get(1).getStringValue()), 0.000001);
    } finally {
      QueryResourceManager.getInstance().endQuery(context.getQueryId());
    }
  }
}
