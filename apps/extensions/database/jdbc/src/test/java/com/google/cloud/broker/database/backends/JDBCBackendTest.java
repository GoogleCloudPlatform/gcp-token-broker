package com.google.cloud.broker.database.backends;

import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Before;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;


public class JDBCBackendTest {

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Before
    public void before() {
        environmentVariables.set("APP_SETTING_DATABASE_BACKEND", "com.google.cloud.broker.database.backends.JDBCBackend");
        environmentVariables.set("APP_SETTING_DATABASE_JDBC_URL", "jdbc:sqlite::memory:");
    }

    /**
     * Returns the number of tables in the database
     */
    private int getNumTables(JDBCBackend backend) {
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
    private String getColumnMetadata(JDBCBackend backend, String table, String column) {
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
        JDBCBackend backend = new JDBCBackend();

        // Check that the database is empty
        assertEquals(getNumTables(backend), 0);

        // Initialize the database
        backend.initializeDatabase();

        // Check that the database now has tables
        assertEquals(getNumTables(backend), 2);

        // Check the RefreshToken table's columns
        assertEquals(getColumnMetadata(backend, "RefreshToken", "id"), "12--2000000000--10--YES--NO");
        assertEquals(getColumnMetadata(backend, "RefreshToken", "value"), "12--2000000000--10--YES--NO");
        assertEquals(getColumnMetadata(backend, "RefreshToken", "creation_time"), "4--2000000000--10--YES--NO");

        // Check the Session table's columns
        assertEquals(getColumnMetadata(backend, "Session", "id"), "4--2000000000--10--YES--YES");
        assertEquals(getColumnMetadata(backend, "Session", "owner"), "12--2000000000--10--YES--NO");
        assertEquals(getColumnMetadata(backend, "Session", "renewer"), "12--2000000000--10--YES--NO");
        assertEquals(getColumnMetadata(backend, "Session", "target"), "12--2000000000--10--YES--NO");
        assertEquals(getColumnMetadata(backend, "Session", "scope"), "12--2000000000--10--YES--NO");
        assertEquals(getColumnMetadata(backend, "Session", "password"), "12--2000000000--10--YES--NO");
        assertEquals(getColumnMetadata(backend, "Session", "expires_at"), "4--2000000000--10--YES--NO");
        assertEquals(getColumnMetadata(backend, "Session", "creation_time"), "4--2000000000--10--YES--NO");
    }

}
