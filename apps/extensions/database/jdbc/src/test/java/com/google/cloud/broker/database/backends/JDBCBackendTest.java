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

import static org.junit.Assert.*;

import java.sql.*;
import java.util.HashMap;
import java.util.UUID;

import org.junit.*;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import com.google.cloud.broker.database.DatabaseObjectNotFound;
import com.google.cloud.broker.oauth.RefreshToken;


public class JDBCBackendTest {

    @ClassRule
    public static final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    private static JDBCBackend backend;

    @BeforeClass
    public static void setupClass() {
        environmentVariables.set("APP_SETTING_DATABASE_JDBC_URL", "jdbc:sqlite::memory:");
        backend = new JDBCBackend();
    }

    @Before
    public void setup() {
        // Initialize the database (i.e. create tables) before every test
        backend.initializeDatabase();
    }

    @After
    public void teardown() {
        // Drop tables after every test
        dropTables();
    }

    private void dropTables() {
        // Delete all tables
        Connection connection = backend.getConnection();
        String[] tables = {"RefreshToken", "Session"};
        Statement statement = null;
        for (String table: tables) {
            try {
                statement = connection.createStatement();
                statement.executeUpdate("DROP TABLE " + table);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            } finally {
                try { if (statement != null) statement.close(); } catch (SQLException e) {throw new RuntimeException(e);}
            }
        }
    }

    /**
     * Returns the number of tables in the database
     */
    private int getNumTables() {
        Connection connection = backend.getConnection();
        try {
            DatabaseMetaData databaseMetaData = connection.getMetaData();
            ResultSet resultSet = databaseMetaData.getTables(null, null, null, new String[]{"TABLE"});
            int numTables = 0;
            while (resultSet.next()) {
                numTables += 1;
            }
            return numTables;
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a column's metadata, presented in a string format for ease-of-comparison.
     */
    private String getColumnMetadata(String table, String column) {
        Connection connection = backend.getConnection();
        try {
            DatabaseMetaData databaseMetaData = connection.getMetaData();
            ResultSet columns = databaseMetaData.getColumns(null,null, table, column);
            columns.next();
            String dataType = columns.getString("DATA_TYPE");
            String columnSize = columns.getString("COLUMN_SIZE");
            String decimalDigits = columns.getString("DECIMAL_DIGITS");
            String isNullable = columns.getString("IS_NULLABLE");
            String isAutoIncrement = columns.getString("IS_AUTOINCREMENT");
            return dataType + "--" + columnSize + "--" + decimalDigits + "--" + isNullable + "--" + isAutoIncrement;
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testInitializeDatabase() {
        // Check that the database is empty
        dropTables();
        assertEquals(getNumTables(), 0);

        // Initialize the database
        backend.initializeDatabase();

        // Check that the database now has tables
        assertEquals(getNumTables(), 2);

        // Check the RefreshToken table's columns
        assertEquals(getColumnMetadata("RefreshToken", "id"), "12--2000000000--10--YES--NO");
        assertEquals(getColumnMetadata("RefreshToken", "value"), "12--2000000000--10--YES--NO");
        assertEquals(getColumnMetadata("RefreshToken", "creation_time"), "4--2000000000--10--YES--NO");

        // Check the Session table's columns
        assertEquals(getColumnMetadata("Session", "id"), "12--2000000000--10--YES--NO");
        assertEquals(getColumnMetadata("Session", "owner"), "12--2000000000--10--YES--NO");
        assertEquals(getColumnMetadata("Session", "renewer"), "12--2000000000--10--YES--NO");
        assertEquals(getColumnMetadata("Session", "target"), "12--2000000000--10--YES--NO");
        assertEquals(getColumnMetadata("Session", "scope"), "12--2000000000--10--YES--NO");
        assertEquals(getColumnMetadata("Session", "password"), "12--2000000000--10--YES--NO");
        assertEquals(getColumnMetadata("Session", "expires_at"), "4--2000000000--10--YES--NO");
        assertEquals(getColumnMetadata("Session", "creation_time"), "4--2000000000--10--YES--NO");
    }

    /**
     * Test saving a new model to the database.
     */
    @Test
    public void testSaveNew() {
        // Check that there are no records
        Connection connection = backend.getConnection();
        PreparedStatement statement = null;
        ResultSet rs = null;
        try {
            String query = "SELECT * from RefreshToken;";
            statement = connection.prepareStatement(query);
            rs = statement.executeQuery();
            assertFalse(rs.next());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException e) {throw new RuntimeException(e);}
            try { if (statement != null) statement.close(); } catch (SQLException e) {throw new RuntimeException(e);}
        }

        // Create a new record
        HashMap<String, Object> values = new HashMap<String, Object>();
        values.put("id", "alice@example.com");
        values.put("creation_time", 1111111111111L);
        values.put("value", "abcd".getBytes());
        RefreshToken token = new RefreshToken(values);
        backend.save(token);

        // Check that the record was correctly created
        try {
            String query = "SELECT * from RefreshToken WHERE id = 'alice@example.com'";
            statement = connection.prepareStatement(query);
            rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(rs.getObject("creation_time"), 1111111111111L);
            assertArrayEquals((byte[]) rs.getObject("value"), "abcd".getBytes());
            assertFalse(rs.next());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException e) {throw new RuntimeException(e);}
            try { if (statement != null) statement.close(); } catch (SQLException e) {throw new RuntimeException(e);}
        }
    }

    /**
     * Test updating an existing model to the database.
     */
    @Test
    public void testUpdate() {
        // Create a record in the database
        Connection connection = backend.getConnection();
        PreparedStatement statement = null;
        try {
            String query = "INSERT INTO RefreshToken (id, creation_time, value) VALUES (?, ?, ?);";
            statement = connection.prepareStatement(query);
            statement.setString(1, "alice@example.com");
            statement.setLong(2, 1111111111111L);
            statement.setBytes(3, "abcd".getBytes());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try { if (statement != null) statement.close(); } catch (SQLException e) {throw new RuntimeException(e);}
        }

        // Update the record with the same ID but different values
        HashMap<String, Object> values = new HashMap<String, Object>();
        values.put("id", "alice@example.com");
        values.put("creation_time", 2222222222222L);
        values.put("value", "xyz".getBytes());
        RefreshToken token = new RefreshToken(values);
        backend.save(token);

        // Check that the record was updated
        ResultSet rs = null;
        try {
            String query = "SELECT * from RefreshToken WHERE id = 'alice@example.com'";
            statement = connection.prepareStatement(query);
            rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(rs.getObject("creation_time"), 2222222222222L);
            assertArrayEquals((byte[]) rs.getObject("value"), "xyz".getBytes());
            assertFalse(rs.next());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException e) {throw new RuntimeException(e);}
            try { if (statement != null) statement.close(); } catch (SQLException e) {throw new RuntimeException(e);}
        }
    }

    /**
     * Test saving a model to the database, without specifying an ID. An ID should automatically be assigned.
     */
    @Test
    public void testSaveWithoutID() {
        // Check that there are no records
        Connection connection = backend.getConnection();
        Statement statement = null;
        ResultSet rs = null;
        try {
            String query = "SELECT * from RefreshToken;";
            statement = connection.createStatement();
            rs = statement.executeQuery(query);
            assertFalse(rs.next());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException e) {throw new RuntimeException(e);}
            try { if (statement != null) statement.close(); } catch (SQLException e) {throw new RuntimeException(e);}
        }

        // Create a new record without specifying an ID
        HashMap<String, Object> values = new HashMap<String, Object>();
        values.put("creation_time", 1111111111111L);
        values.put("value", "abcd".getBytes());
        RefreshToken token = new RefreshToken(values);
        backend.save(token);

        // Check that the record was correctly created
        try {
            String query = "SELECT * from RefreshToken;";
            statement = connection.createStatement();
            rs = statement.executeQuery(query);
            assertTrue(rs.next());
            assertEquals(rs.getObject("creation_time"), 1111111111111L);
            assertArrayEquals((byte[]) rs.getObject("value"), "abcd".getBytes());

            // Check that the ID is a valid UUID
            UUID uuid = UUID.fromString((String) rs.getObject("id"));
            assertEquals(uuid.toString(), rs.getObject("id"));

            assertFalse(rs.next());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException e) {throw new RuntimeException(e);}
            try { if (statement != null) statement.close(); } catch (SQLException e) {throw new RuntimeException(e);}
        }
    }

    /**
     * Test retrieving a model from the database.
     */
    @Test
    public void testGet() {
        // Create a record in the database
        Connection connection = backend.getConnection();
        PreparedStatement statement = null;
        try {
            String query = "INSERT INTO RefreshToken (id, creation_time, value) VALUES (?, ?, ?);";
            statement = connection.prepareStatement(query);
            statement.setString(1, "alice@example.com");
            statement.setLong(2, 1111111111111L);
            statement.setBytes(3, "abcd".getBytes());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try { if (statement != null) statement.close(); } catch (SQLException e) {throw new RuntimeException(e);}
        }

        // Check that the record is correctly retrieved
        RefreshToken token = (RefreshToken) backend.get(RefreshToken.class, "alice@example.com");
        assertEquals(token.getValue("id"), "alice@example.com");
        assertEquals(token.getValue("creation_time"), 1111111111111L);
        assertArrayEquals((byte[]) token.getValue("value"), "abcd".getBytes());
    }

    /**
     * Test retrieving a model that doesn't exist. The DatabaseObjectNotFound exception should be thrown.
     */
    @Test(expected = DatabaseObjectNotFound.class)
    public void testGetNotExist() {
        backend.get(RefreshToken.class, "whatever");
    }

    /**
     * Test deleting a model from the database.
     */
    @Test
    public void testDelete() {
        // Create a record in the database
        Connection connection = backend.getConnection();
        PreparedStatement statement = null;
        try {
            String query = "INSERT INTO RefreshToken (id, creation_time, value) VALUES (?, ?, ?);";
            statement = connection.prepareStatement(query);
            statement.setString(1, "alice@example.com");
            statement.setLong(2, 1111111111111L);
            statement.setBytes(3, "abcd".getBytes());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try { if (statement != null) statement.close(); } catch (SQLException e) {throw new RuntimeException(e);}
        }

        // Delete the record
        HashMap<String, Object> values = new HashMap<String, Object>();
        values.put("id", "alice@example.com");
        RefreshToken token = new RefreshToken(values);
        backend.delete(token);

        // Check that the record was deleted
        ResultSet rs = null;
        try {
            String query = "SELECT * from RefreshToken WHERE id='alice@example.com';";
            statement = connection.prepareStatement(query);
            rs = statement.executeQuery();
            assertFalse(rs.next());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException e) {throw new RuntimeException(e);}
            try { if (statement != null) statement.close(); } catch (SQLException e) {throw new RuntimeException(e);}
        }
    }

}
