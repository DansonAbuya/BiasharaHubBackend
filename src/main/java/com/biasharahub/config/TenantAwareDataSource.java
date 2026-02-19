package com.biasharahub.config;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.springframework.jdbc.datasource.AbstractDataSource;

public class TenantAwareDataSource extends AbstractDataSource {

    private final DataSource delegate;

    public TenantAwareDataSource(DataSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = delegate.getConnection();
        setSchema(connection);
        return connection;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection connection = delegate.getConnection(username, password);
        setSchema(connection);
        return connection;
    }

    private void setSchema(Connection connection) throws SQLException {
        String schema = TenantContext.getTenantSchema();
        if (schema == null) {
            schema = "tenant_default";
        }

        if (!schema.matches("^[a-zA-Z0-9_]+$")) {
            throw new SQLException("Invalid tenant schema name: " + schema);
        }

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET search_path TO " + schema);
        }
    }
}
