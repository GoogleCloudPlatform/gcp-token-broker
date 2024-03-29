// Copyright 2020 Google LLC
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.cloud.broker.database.backends;

import com.google.cloud.broker.database.DatabaseObjectNotFound;
import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.settings.SettingsOverride;
import java.util.Map;
import org.junit.*;

public class SQLiteBackendTest extends JDBCBackendTest {

  private static JDBCBackend backend;

  @ClassRule
  public static SettingsOverride settingsOverride =
      new SettingsOverride(Map.of(AppSettings.DATABASE_JDBC_URL, "jdbc:sqlite::memory:"));

  @BeforeClass
  public static void setupClass() {
    backend = new JDBCBackend();
  }

  @Before
  public void setup() {
    JDBCBackendTest.setup(backend);
  }

  @After
  public void teardown() {
    JDBCBackendTest.teardown(backend);
  }

  @Test
  public void testInitializeDatabase() {
    JDBCBackendTest.initializeDatabase(backend);
  }

  @Test
  public void testSaveNew() {
    JDBCBackendTest.saveNew(backend);
  }

  @Test
  public void testUpdate() {
    JDBCBackendTest.update(backend);
  }

  @Test
  public void testSaveWithoutID() {
    JDBCBackendTest.saveWithoutID(backend);
  }

  @Test
  public void testGet() throws DatabaseObjectNotFound {
    JDBCBackendTest.get(backend);
  }

  @Test
  public void testGetNotExist() {
    JDBCBackendTest.getNotExist(backend);
  }

  @Test
  public void testDelete() {
    JDBCBackendTest.delete(backend);
  }

  @Test
  public void testDeleteExpiredItems() {
    JDBCBackendTest.deleteExpiredItems(backend, false);
  }

  @Test
  public void testDeleteExpiredItemsWithLimit() {
    JDBCBackendTest.deleteExpiredItems(backend, true);
  }
}
