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

import java.util.Map;

import com.google.cloud.broker.settings.SettingsOverride;
import org.junit.*;

import com.google.cloud.broker.settings.AppSettings;


public class PostgreSQLBackendTest extends JDBCBackendTest {

    private static JDBCBackend backend;
    private static SettingsOverride backupSettings;

    @BeforeClass
    public static void setupClass() {
        backend = new JDBCBackend();
        // Override settings
        backupSettings = new SettingsOverride(Map.of(
            AppSettings.DATABASE_JDBC_URL, "jdbc:postgresql:broker?user=testuser&password=UNSECURE-PASSWORD"
        ));
    }

    @AfterClass
    public static void teardDownClass() throws Exception {
        // Restore settings
        backupSettings.restore();
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
        JDBCBackendTest.testInitializeDatabase(backend);
    }

    @Test
    public void testSaveNew() {
        JDBCBackendTest.testSaveNew(backend);
    }

    @Test
    public void testUpdate() {
        JDBCBackendTest.testUpdate(backend);
    }

    @Test
    public void testSaveWithoutID() {
        JDBCBackendTest.testSaveWithoutID(backend);
    }

    @Test
    public void testGet() {
        JDBCBackendTest.testGet(backend);
    }

    @Test
    public void testGetNotExist() {
        JDBCBackendTest.testGetNotExist(backend);
    }

    @Test
    public void testDelete() {
        JDBCBackendTest.testDelete(backend);
    }

}