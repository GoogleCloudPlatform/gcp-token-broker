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

import java.lang.reflect.Constructor;
import java.sql.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.google.cloud.broker.database.DatabaseObjectNotFound;
import com.google.cloud.broker.database.models.Model;
import com.google.cloud.broker.settings.AppSettings;


public class JDBCBackend extends AbstractDatabaseBackend {

    protected AppSettings settings = AppSettings.getInstance();

    @Override
    public Model get(Class modelClass, String objectId) throws DatabaseObjectNotFound {
        Connection connection = null;
        Statement statement = null;
        ResultSet rs = null;
        try {
            String table = modelClass.getSimpleName();
            String query = "SELECT * FROM " + table + " WHERE id = '" + objectId + "'";

            connection = DriverManager.getConnection(settings.getProperty("DATABASE_JDBC_URL"));
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
            Model model = null;
            try {
                Constructor constructor = modelClass.getConstructor(HashMap.class);
                model = (Model) constructor.newInstance(values);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            return model;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException e) {throw new RuntimeException(e);}
            try { if (statement != null) statement.close(); } catch (SQLException e) {throw new RuntimeException(e);}
            try { if (connection != null) connection.close(); } catch (SQLException e) {throw new RuntimeException(e);}
        }
    }

    @Override
    public void insert(Model model) {
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet rs = null;
        try {
            // Assemble the columns and values
            String columns = "";
            String values = "";
            Iterator<Map.Entry<String, Object>> iterator = model.getValues().entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Object> entry = iterator.next();
                String column = entry.getKey();
                Object value = entry.getValue().toString();
                columns += column;
                values += "'" + value + "'";
                if (iterator.hasNext()) {
                    columns += ", ";
                    values += ", ";
                }
            }

            // Assemble the query
            String table = model.getClass().getSimpleName();
            String query = "INSERT INTO " + table + " (" + columns + ") VALUES (" + values + ");";

            // Run the query
            connection = DriverManager.getConnection(settings.getProperty("DATABASE_JDBC_URL"));
            statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
            statement.executeUpdate();

            // Retrieve the auto-generated id
            rs = statement.getGeneratedKeys();
            rs.next();
            int id = rs.getInt(1);
            model.setValue("id", id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException e) {throw new RuntimeException(e);}
            try { if (statement != null) statement.close(); } catch (SQLException e) {throw new RuntimeException(e);}
            try { if (connection != null) connection.close(); } catch (SQLException e) {throw new RuntimeException(e);}
        }
    }

    @Override
    public void update(Model model) {
        Connection connection = null;
        Statement statement = null;
        try {
            // Assemble the columns and values
            String pairs = "";
            Iterator<Map.Entry<String, Object>> iterator = model.getValues().entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Object> entry = iterator.next();
                String column = entry.getKey();
                Object value = entry.getValue().toString();
                pairs += column + " = '" + value + "'";
                if (iterator.hasNext()) {
                    pairs += ", ";
                }
            }

            // Assemble the query
            String table = model.getClass().getSimpleName();
            String objectId = model.getValue("id").toString();
            String query = "UPDATE " + table + " SET " + pairs + " WHERE id = '" + objectId + "'";

            // Run the query
            connection = DriverManager.getConnection(settings.getProperty("DATABASE_JDBC_URL"));
            statement = connection.createStatement();
            statement.executeUpdate(query);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try { if (statement != null) statement.close(); } catch (SQLException e) {throw new RuntimeException(e);}
            try { if (connection != null) connection.close(); } catch (SQLException e) {throw new RuntimeException(e);}
        }
    }

    @Override
    public void delete(Model model) {
        Connection connection = null;
        Statement statement = null;
        try {
            // Assemble the query
            String table = model.getClass().getSimpleName();
            String id = model.getValue("id").toString();
            String query = "DELETE FROM " + table + " WHERE id = '" + id + "'";

            // Run the query
            connection = DriverManager.getConnection(settings.getProperty("DATABASE_JDBC_URL"));
            statement = connection.createStatement();
            statement.executeUpdate(query);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try { if (statement != null) statement.close(); } catch (SQLException e) {throw new RuntimeException(e);}
            try { if (connection != null) connection.close(); } catch (SQLException e) {throw new RuntimeException(e);}
        }
    }

    @Override
    public void initializeDatabase() {
        Connection connection = null;
        Statement statement = null;
        String url = settings.getProperty("DATABASE_JDBC_URL");
        String dialect = url.split(":")[1];
        String autoincrementKey = "";
        String blobType = "";
        switch (dialect) {
            case "sqlite":
                autoincrementKey = "id NOT NULL PRIMARY KEY";
                blobType = "BLOB";
                break;
            case "postgresql":
                autoincrementKey = "id SERIAL PRIMARY KEY";
                blobType = "BYTEA";
                break;
            default:
                throw new RuntimeException("Dialect `" + dialect + "` is not currently supported by the JDBCDatabaseBackend.");
        }
        try {
            String query =
                "CREATE TABLE IF NOT EXISTS session (" +
                    autoincrementKey + "," +
                    "owner VARCHAR(255)," +
                    "renewer VARCHAR(255)," +
                    "target VARCHAR(255)," +
                    "scope VARCHAR(255)," +
                    "password VARCHAR(255)," +
                    "expires_at BIGINT," +
                    "creation_time BIGINT" +
                ");" +
                "CREATE TABLE IF NOT EXISTS refreshtoken (" +
                    "id VARCHAR(255) PRIMARY KEY," +
                    "value " + blobType + "," +
                    "creation_time BIGINT" +
                ");";
            connection = DriverManager.getConnection(settings.getProperty("DATABASE_JDBC_URL"));
            statement = connection.createStatement();
            statement.executeUpdate(query);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try { if (statement != null) statement.close(); } catch (SQLException e) {throw new RuntimeException(e);}
            try { if (connection != null) connection.close(); } catch (SQLException e) {throw new RuntimeException(e);}
        }
    }
}
