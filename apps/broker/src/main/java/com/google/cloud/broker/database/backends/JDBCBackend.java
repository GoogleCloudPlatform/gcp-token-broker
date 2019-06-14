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
        try {
            String table = modelClass.getSimpleName();
            String query = "SELECT * FROM " + table + " WHERE id = '" + objectId + "'";

            Connection connection = DriverManager.getConnection(settings.getProperty("DATABASE_JDBC_URL"));
            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery(query);

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
        }
    }

    @Override
    public void insert(Model model) {
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
            Connection connection = DriverManager.getConnection(settings.getProperty("DATABASE_JDBC_URL"));
            PreparedStatement statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
            statement.executeUpdate();

            // Retrieve the auto-generated id
            ResultSet rs = statement.getGeneratedKeys();
            rs.next();
            int id = rs.getInt(1);
            model.setValue("id", id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void update(Model model) {
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
            String objectId = (String) model.getValue("id");
            String query = "UPDATE " + table + " SET " + pairs + " WHERE id = '" + objectId + "'";

            // Run the query
            Connection connection = DriverManager.getConnection(settings.getProperty("DATABASE_JDBC_URL"));
            Statement statement = connection.createStatement();
            statement.executeUpdate(query);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void delete(Model model) {
        try {
            // Assemble the query
            String table = model.getClass().getSimpleName();
            String id = (String) model.getValue("id");
            String query = "DELETE FROM " + table + " WHERE id = '" + id + "'";

            // Run the query
            Connection connection = DriverManager.getConnection(settings.getProperty("DATABASE_JDBC_URL"));
            Statement statement = connection.createStatement();
            statement.executeUpdate(query);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void initializeDatabase() {
        try {
            String query =
                "CREATE TABLE session (" +
                    "id INTEGER NOT NULL AUTO_INCREMENT," +
                    "owner VARCHAR(255)," +
                    "renewer VARCHAR(255)," +
                    "target VARCHAR(255)," +
                    "scope VARCHAR(255)," +
                    "password VARCHAR(255)," +
                    "expires_at INTEGER," +
                    "creation_time INTEGER," +
                    "PRIMARY KEY(id));" +
                "CREATE TABLE refreshtoken (" +
                    "id INTEGER NOT NULL AUTO_INCREMENT," +
                    "value BLOB," +
                    "creation_time INTEGER," +
                    "PRIMARY KEY(id));";
            Connection connection = DriverManager.getConnection(settings.getProperty("DATABASE_JDBC_URL"));
            Statement statement = connection.createStatement();
            statement.executeUpdate(query);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
