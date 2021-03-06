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

package com.cloudera.labs.envelope.translate;

import com.cloudera.labs.envelope.component.ComponentFactory;
import com.cloudera.labs.envelope.schema.FlatSchema;
import com.google.common.collect.Lists;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.types.DataTypes;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.junit.Test;

import java.sql.Timestamp;

import static com.cloudera.labs.envelope.validate.ValidationAssert.assertNoValidationFailures;
import static org.junit.Assert.assertEquals;

public class TestKVPTranslator {
  @Test
  public void testTranslation() {
    String kvps = "field3=100.9---field6=---field7=true---field2=-99.8---field1=hello---field4=-1---field5=120---field8=2018-03-03T20:23:33.897+04:00";
    
    Config config = ConfigFactory.empty()
        .withValue(KVPTranslator.SCHEMA_CONFIG + "." + ComponentFactory.TYPE_CONFIG_NAME,
            ConfigValueFactory.fromAnyRef("flat")) 
        .withValue(KVPTranslator.SCHEMA_CONFIG + "." + FlatSchema.FIELD_NAMES_CONFIG, 
            ConfigValueFactory.fromIterable(
                Lists.newArrayList("field1", "field2", "field3", "field4",
                                   "field5", "field6", "field7", "field8")))
        .withValue(KVPTranslator.SCHEMA_CONFIG + "." + FlatSchema.FIELD_TYPES_CONFIG,
            ConfigValueFactory.fromIterable(
                Lists.newArrayList("string", "float", "double", "int",
                                   "long", "int", "boolean", "timestamp")))
        .withValue(KVPTranslator.KVP_DELIMITER_CONFIG_NAME, ConfigValueFactory.fromAnyRef("---"))
        .withValue(KVPTranslator.FIELD_DELIMITER_CONFIG_NAME, ConfigValueFactory.fromAnyRef("="));

    KVPTranslator t = new KVPTranslator();
    assertNoValidationFailures(t, config);
    t.configure(config);
    Row raw = TestingMessageFactory.get(kvps, DataTypes.StringType);
    Row r = t.translate(raw).iterator().next();
    assertEquals(r.length(), 8);
    assertEquals(r.get(0), "hello");
    assertEquals(r.get(1), -99.8f);
    assertEquals(r.get(2), 100.9d);
    assertEquals(r.get(3), -1);
    assertEquals(r.get(4), 120L);
    assertEquals(r.get(5), null);
    assertEquals(r.get(6), true);
    assertEquals(((Timestamp)r.get(7)).getTime(), 1520094213897L);
  }

  @Test
  public void testTimestampFormats() {
    String kvps = "field3=100.9---field6=---field7=true---field2=2018-09-09 23:49:29.00000---field1=2018-09-19 23:49:29.92284---field4=-1---field5=120---field8=2018-09-19 00:00:00";

    Config config = ConfigFactory.empty()
        .withValue(KVPTranslator.SCHEMA_CONFIG + "." + ComponentFactory.TYPE_CONFIG_NAME,
            ConfigValueFactory.fromAnyRef("flat")) 
        .withValue(KVPTranslator.SCHEMA_CONFIG + "." + FlatSchema.FIELD_NAMES_CONFIG,
            ConfigValueFactory.fromIterable(
                Lists.newArrayList("field1", "field2", "field3", "field4",
                                   "field5", "field6", "field7", "field8")))
        .withValue(KVPTranslator.SCHEMA_CONFIG + "." + FlatSchema.FIELD_TYPES_CONFIG,
            ConfigValueFactory.fromIterable(
            Lists.newArrayList("timestamp", "timestamp", "double", "int",
                               "long", "int", "boolean", "timestamp")))
        .withValue(KVPTranslator.KVP_DELIMITER_CONFIG_NAME, ConfigValueFactory.fromAnyRef("---"))
        .withValue(KVPTranslator.FIELD_DELIMITER_CONFIG_NAME, ConfigValueFactory.fromAnyRef("="))
        .withValue(DelimitedTranslator.TIMESTAMP_FORMAT_CONFIG_NAME, ConfigValueFactory.fromIterable(
            Lists.newArrayList("yyyy-MM-dd HH:mm:ss.SSSSS", "yyyy-MM-dd HH:mm:ss")));

    KVPTranslator t = new KVPTranslator();
    assertNoValidationFailures(t, config);
    t.configure(config);
    Row raw = TestingMessageFactory.get(kvps, DataTypes.StringType);
    Row r = t.translate(raw).iterator().next();
    assertEquals(r.length(), 8);
    // Timestamp microseconds to miliseconds truncation
    assertEquals(new LocalDateTime(r.get(0)).
        toDateTime(DateTimeZone.UTC).toString(), "2018-09-19T23:49:29.922Z");
    assertEquals(new LocalDateTime(r.get(1)).
        toDateTime(DateTimeZone.UTC).toString(), "2018-09-09T23:49:29.000Z");
    assertEquals(r.get(2), 100.9d);
    assertEquals(r.get(3), -1);
    assertEquals(r.get(4), 120L);
    assertEquals(r.get(5), null);
    assertEquals(r.get(6), true);
    assertEquals(new LocalDateTime(r.get(7)).
        toDateTime(DateTimeZone.UTC).toString(), "2018-09-19T00:00:00.000Z");
  }
}
