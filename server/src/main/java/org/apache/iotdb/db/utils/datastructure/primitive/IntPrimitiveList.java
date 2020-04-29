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
package org.apache.iotdb.db.utils.datastructure.primitive;

import static org.apache.iotdb.db.rescon.PrimitiveArrayPool.ARRAY_SIZE;

import java.util.ArrayList;
import java.util.List;
import org.apache.iotdb.db.rescon.PrimitiveArrayPool;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;

public class IntPrimitiveList extends PrimitiveList {

  private List<int[]> values;

  IntPrimitiveList() {
    super(TSDataType.INT32);
    values = new ArrayList<>();
  }

  @Override
  public void putInt(int value) {
    checkExpansion();
    int arrayIndex = size / ARRAY_SIZE;
    int elementIndex = size % ARRAY_SIZE;
    values.get(arrayIndex)[elementIndex] = value;
    size++;
  }

  @Override
  public int getInt(int index) {
    if (index >= size) {
      throw new ArrayIndexOutOfBoundsException(index);
    }
    int arrayIndex = index / ARRAY_SIZE;
    int elementIndex = index % ARRAY_SIZE;
    return values.get(arrayIndex)[elementIndex];
  }

  @Override
  void clearAndReleaseValues() {
    if (values != null) {
      for (int[] dataArray : values) {
        PrimitiveArrayPool.getInstance().release(dataArray);
      }
      values.clear();
    }
  }

  @Override
  protected void expandValues() {
    values.add((int[]) PrimitiveArrayPool
        .getInstance().getPrimitiveDataListByType(TSDataType.INT32));
    capacity += ARRAY_SIZE;
  }

  @Override
  public IntPrimitiveList clone() {
    IntPrimitiveList cloneList = new IntPrimitiveList();
    cloneAs(cloneList);
    for (int[] valueArray : values) {
      cloneList.values.add(cloneValue(valueArray));
    }
    return cloneList;
  }

  private int[] cloneValue(int[] array) {
    int[] cloneArray = new int[array.length];
    System.arraycopy(array, 0, cloneArray, 0, array.length);
    return cloneArray;
  }

}
