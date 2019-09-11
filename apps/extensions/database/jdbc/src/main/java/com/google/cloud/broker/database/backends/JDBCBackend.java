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

import java.sql.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.google.cloud.broker.database.DatabaseObjectNotFound;
import com.google.cloud.broker.database.models.Model;
import com.google.cloud.broker.settings.AppSettings;


public class JDBCBackend extends AbstractDatabaseBackend {

    protected Connection connectionInstance;

    public Connection getConnection() {
        if (connectionInstance == null) {
            String url = AppSettings.requireProperty("DATABASE_JDBC_URL");
            try {
                connectionInstance = DriverManager.getConnection(url);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return connectionInstance;
    }

    private void formatStatement(int index, PreparedStatement statement, HashMap<String, Object> values) throws SQLException {
        Iterator<Map.Entry<String, Object>> iterator = values.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            Object value = entry.getValue();
            if (value instanceof String) {
                statement.setString(index, (String) value);
            }
            else if (value instanceof Long) {
                statement.setLong(index, (long) value);
            }
            else if (value instanceof byte[]) {
                statement.setBytes(index, (byte[]) value);
            }
            else {
                // TODO extend to other supported types
                throw new UnsupportedOperationException("Unsupported type: " + value.getClass());
            }
            index += 1;
        }
    }

    private void runSimpleQuery(String query) {
        Connection connection = getConnection();
        Statement statement = null;
        try {
            statement = connection.createStatement();
            statement.executeUpdate(query);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try { if (statement != null) statement.close(); } catch (SQLException e) {throw new RuntimeException(e);}
        }
    }

    @Override
    public Model get(Class modelClass, String objectId) throws DatabaseObjectNotFound {
        Connection connection = getConnection();
        Statement statement = null;
        ResultSet rs = null;
        try {
            String table = modelClass.getSimpleName();
            String query = "SELECT * FROM " + quote(table) + " WHERE id = '" + objectId + "'";

            statement = connection.createStatement();
            rs = statement.executeQuery(query);

            // No object found
            if (rs.next() == false) {
                throw new DatabaseObjectNotFound(
                        String.format("%s object not found: %s", modelClass.getSimpleName(), objectId));
            }

            // Load result's values into a hashmap
            HashMap<String, Object> values = new HashMap<>();
            ResultSetMetaData rsmd = rs.getMetaData();
            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                String name = rsmd.getColumnName(i);
                values.put(name, rs.getObject(name));
            }

            // Instantiate a new object
            return Model.fromHashMap(modelClass, values);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException e) {throw new RuntimeException(e);}
            try { if (statement != null) statement.close(); } catch (SQLException e) {throw new RuntimeException(e);}
        }
    }

    @Override
    public void save(Model model) {
        if (model.getDBId() == null) {
            // Assign a  unique ID
            model.setDBId(UUID.randomUUID().toString());
        }

        HashMap<String, Object> hashmap = model.toHashMap();
        Connection connection = getConnection();
        PreparedStatement statement = null;
        ResultSet rs = null;
        try {
            // Assemble the columns and values
            String columns = "";
            String values = "";
            String update = "";
            Iterator<Map.Entry<String, Object>> iterator = hashmap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Object> entry = iterator.next();
                String column = entry.getKey();
                columns += quote(column);
                values += "?";
                update += quote(column) + " = ?";
                if (iterator.hasNext()) {
                    columns += ", ";
                    values += ", ";
                    update += ", ";
                }
            }

            // Assemble the query
            String table = model.getClass().getSimpleName();
            String query = "INSERT INTO " + quote(table) + " (" + columns + ") VALUES (" + values + ") " +
                           getUpsertStatement() + " " + update;

            // Format the statement
            statement = connection.prepareStatement(query);
            formatStatement(1, statement, hashmap);  // Format the INSERT values
            formatStatement(1 + hashmap.size(), statement, hashmap);  // Format the UPDATE values

            // Run the query
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException e) {throw new RuntimeException(e);}
            try { if (statement != null) statement.close(); } catch (SQLException e) {throw new RuntimeException(e);}
        }
    }

    @Override
    public void delete(Model model) {
        String table = model.getClass().getSimpleName();
        String id = model.getDBId();
        String query = "DELETE FROM " + quote(table) + " WHERE id = '" + id + "'";
        runSimpleQuery(query);
    }

    @Override
    public void initializeDatabase() {
        // Note: The tables names and column names are wrapped with backquotes to preserve the case. Otherwise some
        // backends (e.g. Postgres) force the names to lowercase.
        String blobType = getBlobType();
        runSimpleQuery(
            "CREATE TABLE IF NOT EXISTS " + quote("Session") + " (" +
                quote("id") + " VARCHAR(255) PRIMARY KEY," +
                quote("owner") + " VARCHAR(255)," +
                quote("renewer") + " VARCHAR(255)," +
                quote("target") + " VARCHAR(255)," +
                quote("scope") + " VARCHAR(255)," +
                quote("password") + " VARCHAR(255)," +
                quote("expiresAt") + " BIGINT," +
                quote("creationTime") + " BIGINT" +
            ");"
        );
        runSimpleQuery(
            "CREATE TABLE IF NOT EXISTS " + quote("RefreshToken") + " (" +
                quote("id") + " VARCHAR(255) PRIMARY KEY," +
                quote("value") + " " + blobType + "," +
                quote("creationTime") + " BIGINT" +
            ");"
        );
    }

    // Dialect-specific -----------------------------------------------------------------------------------------------

    private static final String DIALECT_NOT_SUPPORTED = "Dialect `%s` is not currently supported by the JDBCDatabaseBackend.";

    public static String getDialect() {
        String url = AppSettings.requireProperty("DATABASE_JDBC_URL");
        return url.split(":")[1];
    }

    public static String quote(String name) {
        String dialect = getDialect();
        switch (dialect) {
            case "sqlite":
            case "mariadb":
            case "mysql":
                return "`" + name + "`";
            case "postgresql":
                return "\"" + name + "\"";
            default:
                throw new UnsupportedOperationException(String.format(DIALECT_NOT_SUPPORTED, dialect));
        }
    }

    public static String getBlobType() {
        String dialect = getDialect();
        switch (dialect) {
            case "sqlite":
            case "mariadb":
            case "mysql":
                return "BLOB";
            case "postgresql":
                return "BYTEA";
            default:
                throw new UnsupportedOperationException(String.format(DIALECT_NOT_SUPPORTED, dialect));
        }
    }

    public static String getUpsertStatement() {
        String dialect = getDialect();
        switch (dialect) {
            case "sqlite":
            case "postgresql":
                return "ON CONFLICT(id) DO UPDATE SET";
            case "mariadb":
            case "mysql":
                return "ON DUPLICATE KEY UPDATE";
            default:
                throw new UnsupportedOperationException(String.format(DIALECT_NOT_SUPPORTED, dialect));
        }
    }
}
