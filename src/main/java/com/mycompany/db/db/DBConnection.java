package com.mycompany.db.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DBConnection {

    private static final String SERVER_URL = "jdbc:mysql://localhost:3306/?sslMode=PREFERRED&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String DATABASE_URL = "jdbc:mysql://localhost:3306/studentdb?sslMode=PREFERRED&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static volatile boolean initialized;

    private DBConnection() {
    }

    public static Connection getConnection() throws SQLException {
        loadDriver();
        initializeDatabase();
        return DriverManager.getConnection(DATABASE_URL, username(), password());
    }

    private static void loadDriver() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("MySQL Connector/J is missing from the web application.", exception);
        }
    }

    private static void initializeDatabase() throws SQLException {
        if (initialized) {
            return;
        }
        synchronized (DBConnection.class) {
            if (initialized) {
                return;
            }
            try (Connection serverConnection = DriverManager.getConnection(SERVER_URL, username(), password());
                 Statement statement = serverConnection.createStatement()) {
                statement.executeUpdate("CREATE DATABASE IF NOT EXISTS studentdb");
            }
            try (Connection databaseConnection = DriverManager.getConnection(DATABASE_URL, username(), password());
                 Statement statement = databaseConnection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS students ("
                        + "id INT PRIMARY KEY AUTO_INCREMENT, "
                        + "name VARCHAR(50), "
                        + "course VARCHAR(50), "
                        + "email VARCHAR(100))");
            }
            initialized = true;
        }
    }

    private static String username() {
        return "root";
    }

    private static String password() {
        
        return "password";
    }
}
