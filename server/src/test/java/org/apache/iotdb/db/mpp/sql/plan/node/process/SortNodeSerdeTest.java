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
package org.apache.iotdb.db.mpp.sql.plan.node.process;

import org.apache.iotdb.common.rpc.thrift.TConsensusGroupId;
import org.apache.iotdb.common.rpc.thrift.TConsensusGroupType;
import org.apache.iotdb.common.rpc.thrift.TRegionReplicaSet;
import org.apache.iotdb.db.exception.metadata.IllegalPathException;
import org.apache.iotdb.db.metadata.path.AlignedPath;
import org.apache.iotdb.db.metadata.path.MeasurementPath;
import org.apache.iotdb.db.metadata.path.PartialPath;
import org.apache.iotdb.db.mpp.common.header.ColumnHeader;
import org.apache.iotdb.db.mpp.sql.plan.node.PlanNodeDeserializeHelper;
import org.apache.iotdb.db.mpp.sql.planner.plan.node.PlanNodeId;
import org.apache.iotdb.db.mpp.sql.planner.plan.node.process.AggregationNode;
import org.apache.iotdb.db.mpp.sql.planner.plan.node.process.DeviceViewNode;
import org.apache.iotdb.db.mpp.sql.planner.plan.node.process.FillNode;
import org.apache.iotdb.db.mpp.sql.planner.plan.node.process.FilterNode;
import org.apache.iotdb.db.mpp.sql.planner.plan.node.process.FilterNullNode;
import org.apache.iotdb.db.mpp.sql.planner.plan.node.process.GroupByLevelNode;
import org.apache.iotdb.db.mpp.sql.planner.plan.node.process.LimitNode;
import org.apache.iotdb.db.mpp.sql.planner.plan.node.process.OffsetNode;
import org.apache.iotdb.db.mpp.sql.planner.plan.node.process.SortNode;
import org.apache.iotdb.db.mpp.sql.planner.plan.node.source.SeriesScanNode;
import org.apache.iotdb.db.mpp.sql.statement.component.FillPolicy;
import org.apache.iotdb.db.mpp.sql.statement.component.FilterNullPolicy;
import org.apache.iotdb.db.mpp.sql.statement.component.OrderBy;
import org.apache.iotdb.db.query.aggregation.AggregationType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.read.common.Path;
import org.apache.iotdb.tsfile.read.expression.impl.SingleSeriesExpression;
import org.apache.iotdb.tsfile.read.filter.operator.Regexp;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class SortNodeSerdeTest {

  @Test
  public void TestSerializeAndDeserialize() throws IllegalPathException {
    FilterNode filterNode =
        new FilterNode(
            new PlanNodeId("TestFilterNode"),
            new SingleSeriesExpression(
                new Path("root.sg.d1"),
                new Regexp(
                    "s1", org.apache.iotdb.tsfile.read.filter.factory.FilterType.VALUE_FILTER)));

    FillNode fillNode = new FillNode(new PlanNodeId("TestFillNode"), FillPolicy.PREVIOUS);
    DeviceViewNode deviceViewNode =
        new DeviceViewNode(new PlanNodeId("TestDeviceMergeNode"), OrderBy.TIMESTAMP_ASC);

    Map<PartialPath, Set<AggregationType>> aggregateFuncMap = new HashMap<>();
    Set<AggregationType> aggregationTypes = new HashSet<>();
    aggregationTypes.add(AggregationType.MAX_TIME);
    aggregateFuncMap.put(
        new MeasurementPath("root.sg.d1.s1", TSDataType.BOOLEAN), aggregationTypes);
    AggregationNode aggregationNode =
        new AggregationNode(new PlanNodeId("TestAggregateNode"), null, aggregateFuncMap, null);
    SeriesScanNode seriesScanNode =
        new SeriesScanNode(
            new PlanNodeId("TestSeriesScanNode"),
            new AlignedPath("s1"),
            new TRegionReplicaSet(
                new TConsensusGroupId(TConsensusGroupType.DataRegion, 1), new ArrayList<>()));
    aggregationNode.addChild(seriesScanNode);
    deviceViewNode.addChildDeviceNode("device", aggregationNode);

    aggregateFuncMap = new HashMap<>();
    aggregationTypes = new HashSet<>();
    aggregationTypes.add(AggregationType.MAX_TIME);
    aggregateFuncMap.put(
        new MeasurementPath("root.sg.d1.s1", TSDataType.BOOLEAN), aggregationTypes);
    aggregationNode =
        new AggregationNode(new PlanNodeId("TestAggregateNode"), null, aggregateFuncMap, null);
    aggregationNode.addChild(seriesScanNode);
    deviceViewNode.addChild(aggregationNode);
    deviceViewNode.addChild(seriesScanNode);

    fillNode.addChild(deviceViewNode);
    filterNode.addChild(fillNode);

    FilterNullNode filterNullNode =
        new FilterNullNode(
            new PlanNodeId("TestFilterNullNode"),
            filterNode,
            FilterNullPolicy.ALL_NULL,
            new ArrayList<>());

    Map<ColumnHeader, ColumnHeader> groupedPathMap = new HashMap<>();
    groupedPathMap.put(
        new ColumnHeader("s1", TSDataType.INT32), new ColumnHeader("s", TSDataType.DOUBLE));
    groupedPathMap.put(
        new ColumnHeader("s2", TSDataType.INT32), new ColumnHeader("a", TSDataType.DOUBLE));
    GroupByLevelNode groupByLevelNode =
        new GroupByLevelNode(
            new PlanNodeId("TestGroupByLevelNode"),
            filterNullNode,
            new int[] {1, 3},
            groupedPathMap);

    LimitNode limitNode = new LimitNode(new PlanNodeId("TestLimitNode"), groupByLevelNode, 3);
    OffsetNode offsetNode = new OffsetNode(new PlanNodeId("TestOffsetNode"), limitNode, 2);

    List<String> orderByStr = new ArrayList<>();
    orderByStr.add("s1");
    SortNode sortNode =
        new SortNode(new PlanNodeId("TestSortNode"), offsetNode, orderByStr, OrderBy.TIMESTAMP_ASC);
    ByteBuffer byteBuffer = ByteBuffer.allocate(2048);
    sortNode.serialize(byteBuffer);
    byteBuffer.flip();
    assertEquals(PlanNodeDeserializeHelper.deserialize(byteBuffer), sortNode);
  }
}
