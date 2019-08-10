// Copyright 2019 Google LLC
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

import org.junit.*;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import com.google.cloud.broker.settings.AppSettings;


public class PostgreSQLBackendTest extends JDBCBackendTest {

    @ClassRule
    public static final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    private static JDBCBackend backend;

    @BeforeClass
    public static void setupClass() {
        AppSettings.reset();
        environmentVariables.set("APP_SETTING_DATABASE_JDBC_URL", "jdbc:postgresql:broker?user=testuser&password=UNSECURE-PASSWORD");
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