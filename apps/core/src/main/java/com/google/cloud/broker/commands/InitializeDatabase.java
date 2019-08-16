package com.google.cloud.broker.commands;

import com.google.cloud.broker.database.backends.AbstractDatabaseBackend;

public class InitializeDatabase {

    public static void main(String[] args) {
        AbstractDatabaseBackend.getInstance().initializeDatabase();
    }

}
