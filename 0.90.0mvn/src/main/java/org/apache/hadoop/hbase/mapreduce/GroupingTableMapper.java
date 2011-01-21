/**
 * Copyright 2007 The Apache Software Foundation
 *
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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.mapreduce;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.mapreduce.Job;

/**
 * Extract grouping columns from input record.
 */
public class GroupingTableMapper
extends TableMapper<ImmutableBytesWritable,Result> implements Configurable {

  /**
   * JobConf parameter to specify the columns used to produce the key passed to
   * collect from the map phase.
   */
  public static final String GROUP_COLUMNS =
    "hbase.mapred.groupingtablemap.columns";

  /** The grouping columns. */
  protected byte [][] columns;
  /** The current configuration. */
  private Configuration conf = null;

  /**
   * Use this before submitting a TableMap job. It will appropriately set up
   * the job.
   *
   * @param table The table to be processed.
   * @param scan  The scan with the columns etc.
   * @param groupColumns  A space separated list of columns used to form the
   * key used in collect.
   * @param mapper  The mapper class.
   * @param job  The current job.
   * @throws IOException When setting up the job fails.
   */
  @SuppressWarnings("unchecked")
  public static void initJob(String table, Scan scan, String groupColumns,
    Class<? extends TableMapper> mapper, Job job) throws IOException {
    TableMapReduceUtil.initTableMapperJob(table, scan, mapper,
        ImmutableBytesWritable.class, Result.class, job);
    job.getConfiguration().set(GROUP_COLUMNS, groupColumns);
  }

  /**
   * Extract the grouping columns from value to construct a new key. Pass the
   * new key and value to reduce. If any of the grouping columns are not found
   * in the value, the record is skipped.
   *
   * @param key  The current key.
   * @param value  The current value.
   * @param context  The current context.
   * @throws IOException When writing the record fails.
   * @throws InterruptedException When the job is aborted.
   */
  @Override
  public void map(ImmutableBytesWritable key, Result value, Context context)
  throws IOException, InterruptedException {
    byte[][] keyVals = extractKeyValues(value);
    if(keyVals != null) {
      ImmutableBytesWritable tKey = createGroupKey(keyVals);
      context.write(tKey, value);
    }
  }

  /**
   * Extract columns values from the current record. This method returns
   * null if any of the columns are not found.
   * <p>
   * Override this method if you want to deal with nulls differently.
   *
   * @param r  The current values.
   * @return Array of byte values.
   */
  protected byte[][] extractKeyValues(Result r) {
    byte[][] keyVals = null;
    ArrayList<byte[]> foundList = new ArrayList<byte[]>();
    int numCols = columns.length;
    if (numCols > 0) {
      for (KeyValue value: r.list()) {
        byte [] column = KeyValue.makeColumn(value.getFamily(),
            value.getQualifier());
        for (int i = 0; i < numCols; i++) {
          if (Bytes.equals(column, columns[i])) {
            foundList.add(value.getValue());
            break;
          }
        }
      }
      if(foundList.size() == numCols) {
        keyVals = foundList.toArray(new byte[numCols][]);
      }
    }
    return keyVals;
  }

  /**
   * Create a key by concatenating multiple column values.
   * <p>
   * Override this function in order to produce different types of keys.
   *
   * @param vals  The current key/values.
   * @return A key generated by concatenating multiple column values.
   */
  protected ImmutableBytesWritable createGroupKey(byte[][] vals) {
    if(vals == null) {
      return null;
    }
    StringBuilder sb =  new StringBuilder();
    for(int i = 0; i < vals.length; i++) {
      if(i > 0) {
        sb.append(" ");
      }
      try {
        sb.append(new String(vals[i], HConstants.UTF8_ENCODING));
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException(e);
      }
    }
    return new ImmutableBytesWritable(Bytes.toBytes(sb.toString()));
  }

  /**
   * Returns the current configuration.
   *
   * @return The current configuration.
   * @see org.apache.hadoop.conf.Configurable#getConf()
   */
  @Override
  public Configuration getConf() {
    return conf;
  }

  /**
   * Sets the configuration. This is used to set up the grouping details.
   *
   * @param configuration  The configuration to set.
   * @see org.apache.hadoop.conf.Configurable#setConf(
   *   org.apache.hadoop.conf.Configuration)
   */
  @Override
  public void setConf(Configuration configuration) {
    this.conf = configuration;
    String[] cols = conf.get(GROUP_COLUMNS, "").split(" ");
    columns = new byte[cols.length][];
    for(int i = 0; i < cols.length; i++) {
      columns[i] = Bytes.toBytes(cols[i]);
    }
  }

}
