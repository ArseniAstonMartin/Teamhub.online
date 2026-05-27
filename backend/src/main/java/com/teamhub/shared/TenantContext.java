package com.teamhub.shared;

import java.util.UUID;

/**
 * Thread-local store for the current request's tenant identifier.
 *
 * <p>Set by {@link TenantContextFilter} at the start of each HTTP request and cleared in the
 * filter's finally block. {@link TenantAwareDataSource} reads this value to propagate it as the
 * {@code app.current_tenant_id} session variable on every borrowed JDBC connection.
 */
public final class TenantContext {

  private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

  /** Returns the tenant ID bound to the current thread, or {@code null} if none is set. */
  public static UUID get() {
    return CURRENT.get();
  }

  /** Binds {@code tenantId} to the current thread. */
  public static void set(UUID tenantId) {
    CURRENT.set(tenantId);
  }

  /** Removes the tenant ID from the current thread. Always call this in a finally block. */
  public static void clear() {
    CURRENT.remove();
  }

  private TenantContext() {}
}
