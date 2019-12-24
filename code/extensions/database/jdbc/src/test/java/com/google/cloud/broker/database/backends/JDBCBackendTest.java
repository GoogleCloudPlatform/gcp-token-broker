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
import java.util.UUID;

import com.google.cloud.broker.database.DatabaseObjectNotFound;
import com.google.cloud.broker.oauth.RefreshToken;
import static com.google.cloud.broker.database.backends.JDBCBackend.quote;


public abstract class JDBCBackendTest {

    // TODO: Still needs tests:
    // - Error when saving or deleting
    // - Check names of tables created by initializeDatabase()


    static void setup(JDBCBackend backend) {
        // Initialize the database (i.e. create tables) before every test
        backend.initializeDatabase();
    }

    static void teardown(JDBCBackend backend) {
        // Drop tables after every test
        dropTables(backend);
    }

    private static void dropTables(JDBCBackend backend) {
        // Delete all tables
        Connection connection = backend.getConnection();
        String[] tables = {"RefreshToken", "Session"};
        Statement statement = null;
        for (String table: tables) {
            try {
                statement = connection.createStatement();
                statement.executeUpdate("DROP TABLE " + quote(table));
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
    private static int getNumTables(JDBCBackend backend) {
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
     * Check that the initialization creates the tables
     */
    static void testInitializeDatabase(JDBCBackend backend) {
        // Check that the database is empty
        dropTables(backend);
        assertEquals(getNumTables(backend), 0);

        // Initialize the database
        backend.initializeDatabase();

        // Check that the database now has tables
        assertEquals(getNumTables(backend), 2);
    }

    /**
     * Test saving a new model to the database.
     */
    static void testSaveNew(JDBCBackend backend) {
        // Check that there are no records
        Connection connection = backend.getConnection();
        PreparedStatement statement = null;
        ResultSet rs = null;
        try {
            String query = "SELECT * from " + quote("RefreshToken");
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
        RefreshToken token = new RefreshToken("alice@altostrat.com", "abcd".getBytes(), 1111111111111L);
        backend.save(token);

        // Check that the record was correctly created
        try {
            String query = "SELECT * from " + quote("RefreshToken") + " WHERE id='alice@altostrat.com'";
            statement = connection.prepareStatement(query);
            rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(rs.getObject("creationTime"), 1111111111111L);
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
    static void testUpdate(JDBCBackend backend) {
        // Create a record in the database
        Connection connection = backend.getConnection();
        PreparedStatement statement = null;
        try {
            String query = "INSERT INTO " + quote("RefreshToken") + " (id, " + quote("creationTime") + " , value) VALUES (?, ?, ?);";
            statement = connection.prepareStatement(query);
            statement.setString(1, "alice@altostrat.com");
            statement.setLong(2, 1111111111111L);
            statement.setBytes(3, "abcd".getBytes());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try { if (statement != null) statement.close(); } catch (SQLException e) {throw new RuntimeException(e);}
        }

        // Update the record with the same ID but different values
        RefreshToken token = new RefreshToken("alice@altostrat.com", "xyz".getBytes(), 2222222222222L);
        backend.save(token);

        // Check that the record was updated
        ResultSet rs = null;
        try {
            String query = "SELECT * from " + quote("RefreshToken") + " WHERE id='alice@altostrat.com'";
            statement = connection.prepareStatement(query);
            rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(rs.getObject("creationTime"), 2222222222222L);
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
    static void testSaveWithoutID(JDBCBackend backend) {
        // Check that there are no records
        Connection connection = backend.getConnection();
        Statement statement = null;
        ResultSet rs = null;
        try {
            String query = "SELECT * from " + quote("RefreshToken");
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
        RefreshToken token = new RefreshToken(null, "abcd".getBytes(), 1111111111111L);
        backend.save(token);

        // Check that the record was correctly created
        try {
            String query = "SELECT * from " + quote("RefreshToken");
            statement = connection.createStatement();
            rs = statement.executeQuery(query);
            assertTrue(rs.next());
            assertEquals(rs.getObject("creationTime"), 1111111111111L);
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
    static void testGet(JDBCBackend backend) {
        // Create a record in the database
        Connection connection = backend.getConnection();
        PreparedStatement statement = null;
        try {
            String query = "INSERT INTO " + quote("RefreshToken") + " (id, " + quote("creationTime") + " , value) VALUES (?, ?, ?);";
            statement = connection.prepareStatement(query);
            statement.setString(1, "alice@altostrat.com");
            statement.setLong(2, 1111111111111L);
            statement.setBytes(3, "abcd".getBytes());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try { if (statement != null) statement.close(); } catch (SQLException e) {throw new RuntimeException(e);}
        }

        // Check that the record is correctly retrieved
        RefreshToken token = (RefreshToken) backend.get(RefreshToken.class, "alice@altostrat.com");
        assertEquals(token.getId(), "alice@altostrat.com");
        assertArrayEquals(token.getValue(), "abcd".getBytes());
        assertEquals(token.getCreationTime().longValue(), 1111111111111L);
    }

    /**
     * Test retrieving a model that doesn't exist. The DatabaseObjectNotFound exception should be thrown.
     */
    static void testGetNotExist(JDBCBackend backend) {
        try {
            backend.get(RefreshToken.class, "whatever");
            fail("DatabaseObjectNotFound not thrown");
        } catch (DatabaseObjectNotFound e) {
            // Expected
        }
    }

    /**
     * Test deleting a model from the database.
     */
    static void testDelete(JDBCBackend backend) {
        // Create a record in the database
        Connection connection = backend.getConnection();
        PreparedStatement statement = null;
        try {
            String query = "INSERT INTO " + quote("RefreshToken") + " (id, " + quote("creationTime") + " , value) VALUES (?, ?, ?);";
            statement = connection.prepareStatement(query);
            statement.setString(1, "alice@altostrat.com");
            statement.setLong(2, 1111111111111L);
            statement.setBytes(3, "abcd".getBytes());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try { if (statement != null) statement.close(); } catch (SQLException e) {throw new RuntimeException(e);}
        }

        // Delete the record
        RefreshToken token = new RefreshToken("alice@altostrat.com", null, null);
        backend.delete(token);

        // Check that the record was deleted
        ResultSet rs = null;
        try {
            String query = "SELECT * from " + quote("RefreshToken") + " WHERE id='alice@altostrat.com';";
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
