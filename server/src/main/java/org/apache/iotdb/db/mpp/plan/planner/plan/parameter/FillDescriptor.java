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

package org.apache.iotdb.db.mpp.plan.planner.plan.parameter;

import org.apache.iotdb.db.mpp.plan.statement.component.FillPolicy;
import org.apache.iotdb.db.query.expression.Expression;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.utils.ReadWriteIOUtils;

import java.nio.ByteBuffer;
import java.util.Objects;

public class FillDescriptor {

  // policy of fill null values
  private final FillPolicy fillPolicy;

  // target column for fill
  private final Expression expression;

  // filled value when fillPolicy is VALUE
  private String fillValueString;
  private TSDataType fillDataType;

  public FillDescriptor(FillPolicy fillPolicy, Expression expression) {
    this.fillPolicy = fillPolicy;
    this.expression = expression;
  }

  public FillDescriptor(
      FillPolicy fillPolicy,
      Expression expression,
      String fillValueString,
      TSDataType fillDataType) {
    this.fillPolicy = fillPolicy;
    this.expression = expression;
    this.fillValueString = fillValueString;
    this.fillDataType = fillDataType;
  }

  public void serialize(ByteBuffer byteBuffer) {
    ReadWriteIOUtils.write(fillPolicy.ordinal(), byteBuffer);
    Expression.serialize(expression, byteBuffer);
    if (fillPolicy == FillPolicy.VALUE) {
      ReadWriteIOUtils.write(fillValueString, byteBuffer);
      ReadWriteIOUtils.write(fillDataType.ordinal(), byteBuffer);
    }
  }

  public static FillDescriptor deserialize(ByteBuffer byteBuffer) {
    FillPolicy fillPolicy = FillPolicy.values()[ReadWriteIOUtils.readInt(byteBuffer)];
    Expression expression = Expression.deserialize(byteBuffer);
    if (fillPolicy == FillPolicy.VALUE) {
      String fillValueString = ReadWriteIOUtils.readString(byteBuffer);
      TSDataType fillDataType = TSDataType.values()[ReadWriteIOUtils.readInt(byteBuffer)];
      return new FillDescriptor(fillPolicy, expression, fillValueString, fillDataType);
    } else {
      return new FillDescriptor(fillPolicy, expression);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    FillDescriptor that = (FillDescriptor) o;
    return fillPolicy == that.fillPolicy
        && Objects.equals(expression, that.expression)
        && Objects.equals(fillValueString, that.fillValueString)
        && fillDataType == that.fillDataType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(fillPolicy, expression, fillValueString, fillDataType);
  }
}
