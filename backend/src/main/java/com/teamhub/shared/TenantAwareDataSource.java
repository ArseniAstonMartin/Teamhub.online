package com.teamhub.shared;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * DataSource wrapper that enforces tenant isolation on every borrowed JDBC connection (PRD §7.2).
 *
 * <p>On each {@link #getConnection()} call, {@link TenantContext#get()} is read:
 *
 * <ul>
 *   <li><b>Tenant present:</b> {@code SET ROLE teamhub_app} switches the session to the
 *       non-superuser application role (required for PostgreSQL RLS to be enforced — superusers
 *       bypass RLS regardless of {@code FORCE ROW LEVEL SECURITY}), then {@code SET
 *       app.current_tenant_id} scopes visibility to that tenant's rows.
 *   <li><b>No tenant context</b> (Flyway, health checks, admin paths): {@code RESET ROLE} and
 *       {@code RESET app.current_tenant_id} restore the login user's role and clear any stale GUC
 *       that may have been left on a pooled connection by a previous borrow.
 * </ul>
 *
 * <p>If {@code applyGuc} throws, the connection is closed before the exception propagates so that
 * the pool lease is never abandoned.
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
    try {
      applyGuc(conn);
    } catch (SQLException e) {
      conn.close();
      throw e;
    }
    return conn;
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    Connection conn = super.getConnection(username, password);
    try {
      applyGuc(conn);
    } catch (SQLException e) {
      conn.close();
      throw e;
    }
    return conn;
  }

  private static void applyGuc(Connection conn) throws SQLException {
    UUID tenantId = TenantContext.get();
    try (Statement st = conn.createStatement()) {
      if (tenantId != null) {
        // Switch to the non-superuser app role so RLS is enforced for this connection.
        // UUID.toString() is safe from SQL injection; parameterised SET is not supported for GUCs.
        st.execute("SET ROLE teamhub_app");
        st.execute("SET app.current_tenant_id = '" + tenantId + "'");
      } else {
        // No tenant context (e.g. Flyway, health checks, admin paths): restore the login role and
        // clear the GUC so a pooled connection cannot carry over a previous tenant's state.
        st.execute("RESET ROLE");
        st.execute("RESET app.current_tenant_id");
      }
    }
  }
}
