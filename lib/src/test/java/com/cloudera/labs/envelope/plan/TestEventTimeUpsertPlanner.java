/*
 * Copyright (c) 2015-2019, Cloudera, Inc. All Rights Reserved.
 *
 * Cloudera, Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"). You may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the
 * License.
 */

package com.cloudera.labs.envelope.plan;

import com.cloudera.labs.envelope.component.ComponentFactory;
import com.cloudera.labs.envelope.spark.RowWithSchema;
import com.cloudera.labs.envelope.utils.PlannerUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static com.cloudera.labs.envelope.validate.ValidationAssert.assertNoValidationFailures;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class TestEventTimeUpsertPlanner {

  private List<Row> arriving;
  private List<Row> existing;
  private StructType keySchema;
  private StructType recordSchema;
  private Map<String, Object> configMap;
  private Config config;
  private EventTimeUpsertPlanner p;

  @Before
  public void before() { 
    arriving = Lists.newArrayList();
    existing = Lists.newArrayList();

    keySchema = DataTypes.createStructType(Lists.newArrayList(
      DataTypes.createStructField("key", DataTypes.StringType, false)));
    recordSchema = DataTypes.createStructType(Lists.newArrayList(
      DataTypes.createStructField("key", DataTypes.StringType, false),
      DataTypes.createStructField("value", DataTypes.StringType, true),
      DataTypes.createStructField("timestamp", DataTypes.LongType, true)));

    configMap = Maps.newHashMap();
    configMap.put(EventTimeUpsertPlanner.KEY_FIELD_NAMES_CONFIG_NAME, Lists.newArrayList("key"));
    configMap.put(EventTimeUpsertPlanner.VALUE_FIELD_NAMES_CONFIG_NAME, Lists.newArrayList("value"));
    configMap.put(EventTimeUpsertPlanner.TIMESTAMP_FIELD_NAMES_CONFIG_NAME, Lists.newArrayList("timestamp"));
    config = ConfigFactory.parseMap(configMap);
  }

  @Test
  public void testNotExisting() {
    p = new EventTimeUpsertPlanner();
    assertNoValidationFailures(p, config);
    p.configure(config);

    arriving.add(new RowWithSchema(recordSchema, "a", "hello", 100L));
    Row key = new RowWithSchema(keySchema, "a");

    List<Row> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 1);
    assertEquals(MutationType.valueOf(planned.get(0).<String>getAs(MutationType.MUTATION_TYPE_FIELD_NAME)), MutationType.INSERT);
  }

  @Test
  public void testEarlierExistingWithNewValues() {
    p = new EventTimeUpsertPlanner();
    assertNoValidationFailures(p, config);
    p.configure(config);

    existing.add(new RowWithSchema(recordSchema, "a", "world", 50L));
    arriving.add(new RowWithSchema(recordSchema, "a", "hello", 100L));
    Row key = new RowWithSchema(keySchema, "a");

    List<Row> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 1);
    assertEquals(MutationType.valueOf(planned.get(0).<String>getAs(MutationType.MUTATION_TYPE_FIELD_NAME)), MutationType.UPDATE);
  }

  @Test
  public void testEarlierExistingWithSameValues() {
    p = new EventTimeUpsertPlanner();
    assertNoValidationFailures(p, config);
    p.configure(config);

    existing.add(new RowWithSchema(recordSchema, "a", "world", 50L));
    arriving.add(new RowWithSchema(recordSchema, "a", "world", 100L));
    Row key = new RowWithSchema(keySchema, "a");

    List<Row> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 0);
  }

  @Test
  public void testLaterExisting() {
    p = new EventTimeUpsertPlanner();
    assertNoValidationFailures(p, config);
    p.configure(config);

    existing.add(new RowWithSchema(recordSchema, "a", "world", 150L));
    arriving.add(new RowWithSchema(recordSchema, "a", "hello", 100L));
    Row key = new RowWithSchema(keySchema, "a");

    List<Row> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 0);
  }

  @Test
  public void testSameTimeExistingWithNewValues() {
    p = new EventTimeUpsertPlanner();
    assertNoValidationFailures(p, config);
    p.configure(config);

    existing.add(new RowWithSchema(recordSchema, "a", "world", 100L));
    arriving.add(new RowWithSchema(recordSchema, "a", "hello", 100L));
    Row key = new RowWithSchema(keySchema, "a");

    List<Row> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 1);
    assertEquals(MutationType.valueOf(planned.get(0).<String>getAs(MutationType.MUTATION_TYPE_FIELD_NAME)), MutationType.UPDATE);
  }

  @Test
  public void testSameTimeExistingWithSameValues() {
    p = new EventTimeUpsertPlanner();
    assertNoValidationFailures(p, config);
    p.configure(config);

    existing.add(new RowWithSchema(recordSchema, "a", "world", 100L));
    arriving.add(new RowWithSchema(recordSchema, "a", "world", 100L));
    Row key = new RowWithSchema(keySchema, "a");

    List<Row> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 0);
  }

  @Test
  public void testOnlyUsesLatestArrivingRecordForAKey() {
    p = new EventTimeUpsertPlanner();
    assertNoValidationFailures(p, config);
    p.configure(config);

    existing.add(new RowWithSchema(recordSchema, "a", "world", 50L));
    arriving.add(new RowWithSchema(recordSchema, "a", "125", 125L));
    arriving.add(new RowWithSchema(recordSchema, "a", "200", 200L));
    arriving.add(new RowWithSchema(recordSchema, "a", "135", 135L));
    Row key = new RowWithSchema(keySchema, "a");

    List<Row> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 1);
    assertEquals(MutationType.valueOf(planned.get(0).<String>getAs(MutationType.MUTATION_TYPE_FIELD_NAME)), MutationType.UPDATE);
    Row Row = planned.get(0);
    assertEquals(Row.get(Row.fieldIndex("value")), "200");
  }

  @Test
  public void testLastUpdated() {
    configMap.put(EventTimeUpsertPlanner.LAST_UPDATED_FIELD_NAME_CONFIG_NAME, "lastupdated");
    config = ConfigFactory.parseMap(configMap);
    p = new EventTimeUpsertPlanner();
    assertNoValidationFailures(p, config);
    p.configure(config);

    arriving.add(new RowWithSchema(recordSchema, "a", "hello", 100L));
    Row key = new RowWithSchema(keySchema, "a");

    List<Row> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 1);
    Row Row = planned.get(0);
    assertNotNull(Row.get(Row.fieldIndex("lastupdated")));
  }

  @Test
  public void testNoLastUpdated() {
    p = new EventTimeUpsertPlanner();
    assertNoValidationFailures(p, config);
    p.configure(config);

    arriving.add(new RowWithSchema(recordSchema, "a", "hello", 100L));
    Row key = new RowWithSchema(keySchema, "a");

    List<Row> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 1);
    assertEquals(planned.get(0).length(), 4); // includes mutation type field
  }
  
  @Test
  public void testNonDefaultTimeModel() {
    config = config
        .withValue(EventTimeUpsertPlanner.EVENT_TIME_MODEL_CONFIG_NAME + "." + ComponentFactory.TYPE_CONFIG_NAME,
            ConfigValueFactory.fromAnyRef("longmillis"))
        .withValue(EventTimeUpsertPlanner.LAST_UPDATED_TIME_MODEL_CONFIG_NAME + "." + ComponentFactory.TYPE_CONFIG_NAME,
            ConfigValueFactory.fromAnyRef("longmillis"));
    
    p = new EventTimeUpsertPlanner();
    assertNoValidationFailures(p, config);
    p.configure(config);

    existing.add(new RowWithSchema(recordSchema, "a", "world", 50L));
    arriving.add(new RowWithSchema(recordSchema, "a", "hello", 100L));
    Row key = new RowWithSchema(keySchema, "a");

    List<Row> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 1);
    assertEquals(MutationType.valueOf(planned.get(0).<String>getAs(MutationType.MUTATION_TYPE_FIELD_NAME)), MutationType.UPDATE);
  }

  @Test
  public void testSurrogateKeyForInsert() {
    configMap.put(EventTimeUpsertPlanner.SURROGATE_KEY_FIELD_NAME_CONFIG_NAME, "surrogate");
    config = ConfigFactory.parseMap(configMap);
    p = new EventTimeUpsertPlanner();
    assertNoValidationFailures(p, config);
    p.configure(config);

    arriving.add(new RowWithSchema(recordSchema, "a", "hello", 100L));
    Row key = new RowWithSchema(keySchema, "a");

    List<Row> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 1);
    assertNotNull(planned.get(0).getAs("surrogate"));
    assertEquals(MutationType.valueOf(planned.get(0).<String>getAs(MutationType.MUTATION_TYPE_FIELD_NAME)), MutationType.INSERT);
  }

  @Test
  public void testNoSurrogateKeyForUpdate() {
    configMap.put(EventTimeUpsertPlanner.SURROGATE_KEY_FIELD_NAME_CONFIG_NAME, "surrogate");
    config = ConfigFactory.parseMap(configMap);
    p = new EventTimeUpsertPlanner();
    assertNoValidationFailures(p, config);
    p.configure(config);

    Row existingRow = new RowWithSchema(recordSchema, "a", "world", 50L);
    existingRow = PlannerUtils.appendSurrogateKey(existingRow, "surrogate");

    existing.add(existingRow);
    arriving.add(new RowWithSchema(recordSchema, "a", "hello", 100L));
    Row key = new RowWithSchema(keySchema, "a");

    List<Row> planned = p.planMutationsForKey(key, arriving, existing);

    assertEquals(planned.size(), 1);
    assertFalse(planned.get(0).schema().getFieldIndex("surrogate").isDefined());
    assertEquals(MutationType.valueOf(planned.get(0).<String>getAs(MutationType.MUTATION_TYPE_FIELD_NAME)), MutationType.UPDATE);
  }

}
