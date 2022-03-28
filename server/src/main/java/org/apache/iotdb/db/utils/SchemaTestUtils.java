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
package org.apache.iotdb.db.utils;

import org.apache.iotdb.db.exception.metadata.MetadataException;
import org.apache.iotdb.db.metadata.path.MeasurementPath;
import org.apache.iotdb.db.metadata.path.PartialPath;
import org.apache.iotdb.db.service.IoTDB;

import java.util.List;

import static org.junit.Assert.assertFalse;

public class SchemaTestUtils {

  public static MeasurementPath getMeasurementPath(String pathPatternString)
      throws MetadataException {
    PartialPath pathPattern = new PartialPath(pathPatternString);
    List<MeasurementPath> measurementPaths = IoTDB.schemaProcessor.getMeasurementPaths(pathPattern);
    assertFalse(measurementPaths.isEmpty());
    return measurementPaths.get(0);
  }
}
