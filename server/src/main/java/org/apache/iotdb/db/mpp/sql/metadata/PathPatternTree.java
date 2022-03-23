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

package org.apache.iotdb.db.mpp.sql.metadata;

import org.apache.iotdb.db.metadata.path.PartialPath;
import org.apache.iotdb.db.mpp.common.filter.QueryFilter;
import org.apache.iotdb.db.query.expression.unary.TimeSeriesOperand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PathPatternTree {

  private PathPatternNode rootNode;

  private final Map<String, List<TimeSeriesOperand>> pathToTimeSeriesOperandMap = new HashMap<>();
  private final Map<String, List<QueryFilter>> pathToQueryFiltersMap = new HashMap<>();

  /**
   * Since IoTDB v0.13, all DDL and DML use patternMatch as default. Before IoTDB v0.13, all DDL and
   * DML use prefixMatch.
   */
  private boolean isPrefixMatchPath;

  public PathPatternTree() {
    this.rootNode = new PathPatternNode("root");
  }

  public PathPatternNode getRootNode() {
    return rootNode;
  }

  public void setRootNode(PathPatternNode rootNode) {
    this.rootNode = rootNode;
  }

  public boolean isPrefixMatchPath() {
    return isPrefixMatchPath;
  }

  public void setPrefixMatchPath(boolean prefixMatchPath) {
    isPrefixMatchPath = prefixMatchPath;
  }

  public void search(TimeSeriesOperand expression) {
    PartialPath path = expression.getPath();
    pathToTimeSeriesOperandMap
        .computeIfAbsent(path.getFullPath(), k -> new ArrayList<>())
        .add(expression);
    search(path);
  }

  public void search(QueryFilter filter) {
    PartialPath path = filter.getSinglePath();
    pathToQueryFiltersMap.computeIfAbsent(path.getFullPath(), k -> new ArrayList<>()).add(filter);
    search(path);
  }

  public void search(PartialPath path) {}
}
