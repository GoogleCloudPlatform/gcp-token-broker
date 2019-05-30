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

import com.google.cloud.broker.database.DatabaseObjectNotFound;
import com.google.cloud.broker.database.models.Model;
import com.google.cloud.broker.settings.AppSettings;


public class JDBCBackend extends AbstractDatabaseBackend {

    protected AppSettings settings = AppSettings.getInstance();

    @Override
    public Model get(Class modelClass, String objectId) throws DatabaseObjectNotFound {
        try {
            Connection connection = DriverManager.getConnection(settings.getProperty("DATABASE_JDBC_URL"));
            Statement stmt = connection.createStatement();
            String query = String.format("SELECT * FROM %s WHERE id = %s", modelClass.getSimpleName(), objectId);
            ResultSet rs = stmt.executeQuery(query);

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
    public void save(Model model) {
        try {
            Connection connection = DriverManager.getConnection(settings.getProperty("DATABASE_JDBC_URL"));
            // TODO: If object doesn't exist, insert. Otherwise, update.
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void delete(Model model) {
        try {
            Connection connection = DriverManager.getConnection(settings.getProperty("DATABASE_JDBC_URL"));
            Statement stmt = connection.createStatement();
            String query = String.format("DELETE FROM %s WHERE id = %s", model.getClass().getSimpleName(), model.getValue("id"));
            stmt.executeQuery(query);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
