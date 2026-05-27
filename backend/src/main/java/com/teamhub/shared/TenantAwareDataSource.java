package com.teamhub.shared;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * DataSource wrapper that propagates the tenant GUC ({@code app.current_tenant_id}) to every
 * borrowed JDBC connection, implementing the connection-level side of PRD §7.2.
 *
 * <p>On each {@link #getConnection()} call, {@link TenantContext#get()} is read and the GUC is SET
 * (tenant present) or RESET (no tenant context) before the connection is handed to the caller.
 * Because every borrow overwrites the GUC, stale values from previous borrows in the connection
 * pool are always corrected before first use, making this approach safe for Hikari pooling.
 *
 * <p>Registered transparently via {@link TenantDataSourceConfig} so all Spring Boot connection pool
 * settings, Flyway, and health-check infrastructure continue to work unchanged.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

  public TenantAwareDataSource(DataSource delegate) {
    super(delegate);
  }

  @Override
  public Connection getConnection() throws SQLException {
    Connection conn = super.getConnection();
    applyGuc(conn);
    return conn;
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    Connection conn = super.getConnection(username, password);
    applyGuc(conn);
    return conn;
  }

  private static void applyGuc(Connection conn) throws SQLException {
    UUID tenantId = TenantContext.get();
    try (Statement st = conn.createStatement()) {
      if (tenantId != null) {
        // UUID.toString() is safe from SQL injection; parameterised SET is not supported for GUCs.
        st.execute("SET app.current_tenant_id = '" + tenantId + "'");
      } else {
        st.execute("RESET app.current_tenant_id");
      }
    }
  }
}
