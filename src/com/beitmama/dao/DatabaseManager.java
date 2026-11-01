package com.beitmama.dao;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DatabaseManager {

    
    private static final boolean USE_MYSQL = false; 

    private static final String MYSQL_URL = "jdbc:mysql://localhost:3306/beit_mama?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASS = "";

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {}
    }

    public static Connection getConnection() throws SQLException {
        if (USE_MYSQL) {
            return DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASS);
        } else {
            return DriverManager.getConnection(sqliteUrl());
        }
    }

    public static void initializeDatabase() {
        if (Boolean.parseBoolean(System.getenv("BEITMAMA_PRODUCTION"))) {
            String adminPassword = System.getenv("BEITMAMA_ADMIN_PASSWORD");
            if (adminPassword == null || adminPassword.length() < 16) {
                throw new IllegalStateException(
                        "BEITMAMA_ADMIN_PASSWORD must contain at least 16 characters in production");
            }
        }
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            
            
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS users (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT NOT NULL, " +
                    "email TEXT UNIQUE NOT NULL, " +
                    "phone TEXT NOT NULL, " +
                    "address TEXT, " +
                    "preferred_contact TEXT NOT NULL DEFAULT 'phone', " +
                    "password_hash TEXT NOT NULL, " +
                    "salt TEXT NOT NULL, " +
                    "role TEXT NOT NULL, " +
                    "status TEXT NOT NULL DEFAULT 'ACTIVE', " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            ensureColumn(conn, "users", "address", "TEXT");
            ensureColumn(conn, "users", "preferred_contact", "VARCHAR(20) NOT NULL DEFAULT 'phone'");

            
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS invitations (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "token_hash TEXT UNIQUE NOT NULL, " +
                    "created_by INTEGER NOT NULL, " +
                    "email_or_phone TEXT NOT NULL, " +
                    "expires_at TIMESTAMP NOT NULL, " +
                    "used_at TIMESTAMP, " +
                    "used_by INTEGER, " +
                    "revoked_at TIMESTAMP, " +
                    "FOREIGN KEY(created_by) REFERENCES users(id))");

            
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS seller_profiles (" +
                    "user_id INTEGER PRIMARY KEY, " +
                    "bio TEXT, " +
                    "pickup_address TEXT, " +
                    "region TEXT, " +
                    "approval_status TEXT NOT NULL DEFAULT 'PENDING', " +
                    "pickup_instructions TEXT, " +
                    "FOREIGN KEY(user_id) REFERENCES users(id))");
            ensureColumn(conn, "seller_profiles", "region", "TEXT");

            
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS categories (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT UNIQUE NOT NULL)");

            
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS products (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "seller_id INTEGER NOT NULL, " +
                    "category_id INTEGER NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "description TEXT, " +
                    "price DECIMAL(10,2) NOT NULL, " +
                    "quantity_available INTEGER NOT NULL DEFAULT 0, " +
                    "is_available INTEGER NOT NULL DEFAULT 1, " +
                    "image_url TEXT, " +
                    "FOREIGN KEY(seller_id) REFERENCES users(id), " +
                    "FOREIGN KEY(category_id) REFERENCES categories(id))");
            ensureColumn(conn, "products", "available_until", "TEXT");

            
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS orders (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "customer_id INTEGER NOT NULL, " +
                    "seller_id INTEGER NOT NULL, " +
                    "status TEXT NOT NULL DEFAULT 'pending', " +
                    "total_amount DECIMAL(10,2) NOT NULL, " +
                    "payment_method TEXT NOT NULL DEFAULT 'cash_on_pickup', " +
                    "pickup_time TEXT NOT NULL, " +
                    "delivery_method TEXT NOT NULL DEFAULT 'pickup', " +
                    "delivery_address TEXT, " +
                    "customer_phone_snapshot TEXT, " +
                    "contact_method TEXT NOT NULL DEFAULT 'phone', " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY(customer_id) REFERENCES users(id), " +
                    "FOREIGN KEY(seller_id) REFERENCES users(id))");
            ensureColumn(conn, "orders", "delivery_method", "TEXT NOT NULL DEFAULT 'pickup'");
            ensureColumn(conn, "orders", "delivery_address", "TEXT");
            ensureColumn(conn, "orders", "customer_phone_snapshot", "TEXT");
            ensureColumn(conn, "orders", "contact_method", "VARCHAR(20) NOT NULL DEFAULT 'phone'");

            
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS order_items (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "order_id INTEGER NOT NULL, " +
                    "product_id INTEGER NOT NULL, " +
                    "quantity INTEGER NOT NULL, " +
                    "unit_price DECIMAL(10,2) NOT NULL, " +
                    "subtotal DECIMAL(10,2) NOT NULL, " +
                    "FOREIGN KEY(order_id) REFERENCES orders(id), " +
                    "FOREIGN KEY(product_id) REFERENCES products(id))");

            
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS reviews (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "order_id INTEGER UNIQUE NOT NULL, " +
                    "customer_id INTEGER NOT NULL, " +
                    "seller_id INTEGER NOT NULL, " +
                    "rating INTEGER NOT NULL, " +
                    "comment TEXT, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY(order_id) REFERENCES orders(id))");

            
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS admin_logs (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "admin_id INTEGER NOT NULL, " +
                    "action TEXT NOT NULL, " +
                    "details TEXT, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            
            seedInitialData(conn);

        } catch (SQLException e) {
            System.err.println("Database initialization error: " + e.getMessage());
        }
    }

    private static String sqliteUrl() throws SQLException {
        String configuredPath = System.getenv("BEITMAMA_SQLITE_PATH");
        Path databasePath = Path.of(configuredPath == null || configuredPath.isBlank()
                ? "db/beit_mama.db"
                : configuredPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(databasePath.getParent());
        } catch (IOException e) {
            throw new SQLException("Unable to create SQLite database directory", e);
        }
        return "jdbc:sqlite:" + databasePath;
    }

    private static void ensureColumn(Connection conn, String table, String column, String definition) throws SQLException {
        if (conn.getMetaData().getDatabaseProductName().toLowerCase().contains("sqlite")) {
            try (Statement statement = conn.createStatement();
                 ResultSet columns = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
                while (columns.next()) {
                    if (column.equalsIgnoreCase(columns.getString("name"))) return;
                }
            }
        } else {
            DatabaseMetaData metadata = conn.getMetaData();
            try (ResultSet columns = metadata.getColumns(conn.getCatalog(), null, table, column)) {
                if (columns.next()) return;
            }
        }
        try (Statement statement = conn.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private static void seedInitialData(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            
            String salt = com.beitmama.util.SecurityUtil.generateSalt();
            String configuredPassword = System.getenv("BEITMAMA_ADMIN_PASSWORD");
            String passHash = com.beitmama.util.SecurityUtil.hashPassword(
                    configuredPassword == null || configuredPassword.isBlank() ? "admin123" : configuredPassword,
                    salt);
            stmt.executeUpdate("INSERT OR IGNORE INTO users (id, name, email, phone, password_hash, salt, role, status) " +
                    "VALUES (1, 'مدير النظام', 'admin@beitmama.com', '01000000000', '" + passHash + "', '" + salt + "', 'ADMIN', 'ACTIVE')");

            
            stmt.executeUpdate("INSERT OR IGNORE INTO categories (id, name) VALUES (1, 'محاشي ومحمر')");
            stmt.executeUpdate("INSERT OR IGNORE INTO categories (id, name) VALUES (2, 'طواجن ومكرونات')");
            stmt.executeUpdate("INSERT OR IGNORE INTO categories (id, name) VALUES (3, 'مخبوزات وفطائر')");
            stmt.executeUpdate("INSERT OR IGNORE INTO categories (id, name) VALUES (4, 'حلويات شرقية')");
        } catch (SQLException ignored) {}
    }
}
