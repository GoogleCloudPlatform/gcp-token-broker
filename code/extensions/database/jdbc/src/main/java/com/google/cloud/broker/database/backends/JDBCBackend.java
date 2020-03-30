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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.*;
import java.util.*;

import com.google.cloud.broker.checks.CheckResult;
import com.google.cloud.broker.database.DatabaseObjectNotFound;
import com.google.cloud.broker.database.models.Model;
import com.google.cloud.broker.settings.AppSettings;


public class JDBCBackend extends AbstractDatabaseBackend {

    private Connection connectionInstance;

    Connection getConnection() {
        if (connectionInstance == null) {
            String url = AppSettings.getInstance().getString(AppSettings.DATABASE_JDBC_URL);
            try {
                connectionInstance = DriverManager.getConnection(url);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return connectionInstance;
    }

    private void formatValues(PreparedStatement statement, Map<String, Object> values) throws SQLException {
        formatValues(statement, values, 1);
    }

    private void formatValues(PreparedStatement statement, Map<String, Object> values, int offset) throws SQLException {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object value = entry.getValue();
            formatValue(statement, value, offset);
            offset += 1;
        }
    }

    private void formatValue(PreparedStatement statement, Object value, int index) throws SQLException {
        if (value instanceof String) {
            statement.setString(index, (String) value);
        } else if (value instanceof Integer) {
            statement.setInt(index, (int) value);
        } else if (value instanceof Long) {
            statement.setLong(index, (long) value);
        } else if (value instanceof byte[]) {
            statement.setBytes(index, (byte[]) value);
        } else {
            // TODO extend to other supported types
            throw new UnsupportedOperationException("Unsupported type: " + value.getClass());
        }
    }

    private Model convertResultSetToModel(ResultSet rs, Class modelClass) throws SQLException {
        // Load result's values into a hashmap
        HashMap<String, Object> values = new HashMap<>();
        ResultSetMetaData rsmd = rs.getMetaData();
        for (int i = 1; i <= rsmd.getColumnCount(); i++) {
            String name = rsmd.getColumnName(i);
            values.put(name, rs.getObject(name));
        }

        // Instantiate a new object
        return Model.fromMap(modelClass, values);
    }

    public List<Model> getAll(Class modelClass) {
        Connection connection = getConnection();
        PreparedStatement statement = null;
        ResultSet rs = null;
        try {
            String table = modelClass.getSimpleName();
            String query = "SELECT * FROM " + quote(table);
            statement = connection.prepareStatement(query);
            rs = statement.executeQuery();
            List<Model> models = new ArrayList<>();
            while (rs.next()) {
                Model model = convertResultSetToModel(rs, modelClass);
                models.add(model);
            }
            return models;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException e) {throw new RuntimeException(e);}
            try { if (statement != null) statement.close(); } catch (SQLException e) {throw new RuntimeException(e);}
        }
    }

    @Override
    public Model get(Class modelClass, String objectId) throws DatabaseObjectNotFound {
        Connection connection = getConnection();
        PreparedStatement statement = null;
        ResultSet rs = null;
        try {
            String table = modelClass.getSimpleName();
            String query = "SELECT * FROM " + quote(table) + " WHERE " + quote("id") + " = ?";
            statement = connection.prepareStatement(query);
            formatValue(statement, objectId, 1);
            rs = statement.executeQuery();

            // No object found
            if (!rs.next()) {
                throw new DatabaseObjectNotFound(
                    String.format("%s object not found: %s", modelClass.getSimpleName(), objectId));
            }

            return convertResultSetToModel(rs, modelClass);
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

        Map<String, Object> map = model.toMap();
        Connection connection = getConnection();
        PreparedStatement statement = null;
        try {
            // Assemble the columns and values
            StringBuilder columns = new StringBuilder();
            StringBuilder values = new StringBuilder();
            StringBuilder update = new StringBuilder();
            Iterator<Map.Entry<String, Object>> iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Object> entry = iterator.next();
                String column = entry.getKey();
                columns.append(quote(column));
                values.append("?");
                update.append(quote(column)).append(" = ?");
                if (iterator.hasNext()) {
                    columns.append(", ");
                    values.append(", ");
                    update.append(", ");
                }
            }

            // Assemble the query
            String table = model.getClass().getSimpleName();
            String query = "INSERT INTO " + quote(table) + " (" + columns + ") VALUES (" + values + ") " +
                           getUpsertStatement() + " " + update;

            // Format the statement
            statement = connection.prepareStatement(query);
            formatValues(statement, map);  // Format the INSERT values
            formatValues(statement, map, 1 + map.size());  // Format the UPDATE values

            // Run the query
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try { if (statement != null) statement.close(); } catch (SQLException e) {throw new RuntimeException(e);}
        }
    }

    @Override
    public void delete(Model model) {
        String table = model.getClass().getSimpleName();
        String id = model.getDBId();
        String query = "DELETE FROM " + quote(table) + " WHERE " + quote("id") + "  = ?";
        Connection connection = getConnection();
        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(query);
            formatValue(statement, id, 1);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try { if (statement != null) statement.close(); } catch (SQLException e) {throw new RuntimeException(e);}
        }
    }

    @Override
    public int deleteExpiredItems(Class modelClass, String field, Long cutoffTime, Integer numItems) {
        Connection connection = getConnection();
        String table = modelClass.getSimpleName();
        PreparedStatement statement = null;
        try {
            String query;
            if (numItems != null && numItems > 0) {
                if (getDialect().equals("mariadb") || getDialect().equals("mysql")) {
                    query = "DELETE FROM " + quote(table) + " WHERE " + quote(field) + " <= ? ORDER BY " + quote(field) + " ASC LIMIT ?";
                }
                else {
                    query = "DELETE FROM " + quote(table) + "WHERE " + getRowIdField() + " IN (SELECT " + getRowIdField() + " FROM" + quote(table) + " WHERE " + quote(field) + " <= ? ORDER BY " + quote(field) + " LIMIT ?)";
                }
                statement = connection.prepareStatement(query);
                formatValue(statement, cutoffTime, 1);
                formatValue(statement, numItems, 2);
            }
            else {
                query = "DELETE FROM " + quote(table) + " WHERE " + quote(field) + " <= ?";
                statement = connection.prepareStatement(query);
                formatValue(statement, cutoffTime, 1);
            }
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try { if (statement != null) statement.close(); } catch (SQLException e) {throw new RuntimeException(e);}
        }
    }

    @Override
    public void initializeDatabase() {
        Connection connection = getConnection();
        PreparedStatement statement = null;

        // Note: The tables names and column names are wrapped with quotes to preserve the case. Otherwise some
        // backends (e.g. Postgres) force the names to be lowercased.

        // Create the Session table
        String blobType = getBlobType();
        String query =
            "CREATE TABLE IF NOT EXISTS " + quote("Session") + " (" +
                quote("id") + " VARCHAR(255) PRIMARY KEY," +
                quote("owner") + " VARCHAR(255)," +
                quote("renewer") + " VARCHAR(255)," +
                quote("target") + " VARCHAR(255)," +
                quote("scope") + " VARCHAR(255)," +
                quote("expiresAt") + " BIGINT," +
                quote("creationTime") + " BIGINT" +
            ");";
        try {
            statement = connection.prepareStatement(query);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try { if (statement != null) statement.close(); } catch (SQLException e) {throw new RuntimeException(e);}
        }

        // Create the RefreshToken table
        query =
            "CREATE TABLE IF NOT EXISTS " + quote("RefreshToken") + " (" +
                quote("id") + " VARCHAR(255) PRIMARY KEY," +
                quote("value") + " " + blobType + "," +
                quote("creationTime") + " BIGINT" +
            ");";
        try {
            statement = connection.prepareStatement(query);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try { if (statement != null) statement.close(); } catch (SQLException e) {throw new RuntimeException(e);}
        }
    }

    @Override
    public CheckResult checkConnection() {
        try {
            getConnection();
            return new CheckResult(true);
        } catch(Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            return new CheckResult(false, sw.toString());
        }
    }

    // Dialect-specific -----------------------------------------------------------------------------------------------

    private static final String DIALECT_NOT_SUPPORTED = "Dialect `%s` is not currently supported by the JDBCDatabaseBackend.";

    private static String getDialect() {
        String url = AppSettings.getInstance().getString(AppSettings.DATABASE_JDBC_URL);
        return url.split(":")[1];
    }

    static String quote(String name) {
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

    private static String getBlobType() {
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

    private static String getUpsertStatement() {
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

    private static String getRowIdField() {
        String dialect = getDialect();
        switch (dialect) {
            case "postgresql":
                return "ctid";
            case "sqlite":
                return "rowid";
            default:
                throw new UnsupportedOperationException(String.format(DIALECT_NOT_SUPPORTED, dialect));
        }
    }
}
