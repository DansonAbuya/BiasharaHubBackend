package com.biasharahub.config;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ensures the application database exists before Spring Boot connects.
 * Connects to the default 'postgres' database and creates the target DB if missing.
 * Reads credentials from application.yml, env vars, and system properties.
 */
public final class DatabaseBootstrap {

    private static final String DEFAULT_DB_NAME = "biasharahub";
    private static final String SYSTEM_DB = "postgres";

    private DatabaseBootstrap() {}

    /**
     * Creates the application database if it does not exist.
     * Set DB_SKIP_BOOTSTRAP=true to disable (e.g. when DB is pre-created).
     */
    public static void ensureDatabaseExists() {
        if ("true".equalsIgnoreCase(System.getenv("DB_SKIP_BOOTSTRAP"))
                || "true".equalsIgnoreCase(System.getProperty("db.skip.bootstrap"))) {
            return;
        }

        String url = getConfig("url", "jdbc:postgresql://localhost:5432/" + DEFAULT_DB_NAME);
        String username = getConfig("username", "postgres");
        String password = getConfig("password", "postgres");

        String dbName = extractDatabaseName(url);
        if (dbName == null || dbName.isEmpty()) dbName = DEFAULT_DB_NAME;

        String bootstrapUrl = toBootstrapUrl(url);

        try (Connection conn = DriverManager.getConnection(bootstrapUrl, username, password);
             Statement st = conn.createStatement()) {

            if (databaseExists(st, dbName)) {
                return;
            }

            conn.setAutoCommit(true);
            st.execute("CREATE DATABASE " + dbName);
            System.out.println("[BiasharaHub] Database '" + dbName + "' created.");
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("password authentication failed")) {
                System.err.println("[BiasharaHub] PostgreSQL authentication failed. Set your credentials:");
                System.err.println("  - Environment: DB_USERNAME and DB_PASSWORD");
                System.err.println("  - Or system properties: -Dspring.datasource.username=... -Dspring.datasource.password=...");
                System.err.println("  - Or create the database manually: scripts\\init-database.bat");
                throw new RuntimeException("Database bootstrap: invalid PostgreSQL credentials. " +
                        "Update application.yml or set DB_USERNAME/DB_PASSWORD to match your PostgreSQL login.", e);
            }
            System.err.println("[BiasharaHub] Could not ensure database exists: " + msg);
            throw new RuntimeException("Database bootstrap failed. Create '" + dbName + "' manually or run scripts/init-database.bat", e);
        }
    }

    private static String loadFromYaml(String resource, String key) {
        try (InputStream in = DatabaseBootstrap.class.getResourceAsStream(resource)) {
            if (in == null) return null;
            Map<String, Object> root = new org.yaml.snakeyaml.Yaml().load(in);
            if (root == null) return null;
            Object spring = root.get("spring");
            if (!(spring instanceof Map)) return null;
            Object ds = ((Map<?, ?>) spring).get("datasource");
            if (!(ds instanceof Map)) return null;
            Object val = ((Map<?, ?>) ds).get(key);
            return val == null ? null : String.valueOf(val);
        } catch (Exception e) {
            return null;
        }
    }

    private static String getConfig(String key, String fallback) {
        String sysProp = System.getProperty("spring.datasource." + key);
        if (sysProp != null) return sysProp;

        String env = switch (key) {
            case "url" -> System.getenv("SPRING_DATASOURCE_URL");
            case "username" -> System.getenv("SPRING_DATASOURCE_USERNAME");
            case "password" -> System.getenv("SPRING_DATASOURCE_PASSWORD");
            default -> null;
        };
        if (env != null) return env;
        env = switch (key) {
            case "url" -> System.getenv("DB_URL");
            case "username" -> System.getenv("DB_USERNAME");
            case "password" -> System.getenv("DB_PASSWORD");
            default -> null;
        };
        if (env != null) return env;

        String val = loadFromYaml("/application.yml", key);
        if (val != null) return resolvePlaceholders(val);
        String profile = System.getProperty("spring.profiles.active");
        if (profile == null) profile = System.getenv("SPRING_PROFILES_ACTIVE");
        if (profile != null) {
            val = loadFromYaml("/application-" + profile.trim() + ".yml", key);
            if (val != null) return resolvePlaceholders(val);
        }
        return fallback;
    }

    private static String resolvePlaceholders(String value) {
        Pattern p = Pattern.compile("\\$\\{([^:}]+)(?::([^}]*))?\\}");
        Matcher m = p.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String envVar = m.group(1);
            String def = m.group(2);
            String env = System.getenv(envVar);
            m.appendReplacement(sb, Matcher.quoteReplacement(env != null ? env : (def != null ? def : "")));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String extractDatabaseName(String url) {
        Pattern p = Pattern.compile("jdbc:postgresql://[^/]+/([^?]+)");
        Matcher m = p.matcher(url);
        return m.find() ? m.group(1).trim() : null;
    }

    private static String toBootstrapUrl(String url) {
        if (!url.contains("postgresql")) return url;
        Pattern p = Pattern.compile("^(jdbc:postgresql://[^/]+/)([^?]+)(\\?.*)?$");
        Matcher m = p.matcher(url);
        if (m.matches()) {
            return m.group(1) + SYSTEM_DB + (m.group(3) != null ? m.group(3) : "");
        }
        return url.replaceAll("/[^/]+(\\?|$)", "/" + SYSTEM_DB + "$1");
    }

    private static boolean databaseExists(Statement st, String dbName) throws SQLException {
        if (dbName == null || !dbName.matches("[a-zA-Z0-9_]+")) return true;
        try (ResultSet rs = st.executeQuery(
                "SELECT 1 FROM pg_database WHERE datname = '" + dbName + "'")) {
            return rs.next();
        }
    }
}
