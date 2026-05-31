package com.teamhub.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Append-only audit log row (PRD §19).
 *
 * <p>No UPDATE or DELETE is permitted at the database level: {@code app_audit_writer} (the only
 * role that can INSERT) does not have UPDATE or DELETE privileges; {@code teamhub_app} (the normal
 * app role) has SELECT only. This class is therefore intentionally immutable after construction —
 * no setters, no JPA lifecycle callbacks that would trigger UPDATE.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "actor_user_id")
  private UUID actorUserId;

  @Column(name = "action", nullable = false)
  private String action;

  @Column(name = "resource_type", nullable = false)
  private String resourceType;

  @Column(name = "resource_id")
  private UUID resourceId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "before_state", columnDefinition = "jsonb")
  private Object beforeState;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "after_state", columnDefinition = "jsonb")
  private Object afterState;

  @Column(name = "ip_address", columnDefinition = "inet")
  private String ipAddress;

  @Column(name = "user_agent")
  private String userAgent;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  protected AuditLog() {}

  private AuditLog(Builder builder) {
    this.tenantId = builder.tenantId;
    this.actorUserId = builder.actorUserId;
    this.action = builder.action;
    this.resourceType = builder.resourceType;
    this.resourceId = builder.resourceId;
    this.beforeState = builder.beforeState;
    this.afterState = builder.afterState;
    this.ipAddress = builder.ipAddress;
    this.userAgent = builder.userAgent;
    this.occurredAt = builder.occurredAt != null ? builder.occurredAt : Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getActorUserId() {
    return actorUserId;
  }

  public String getAction() {
    return action;
  }

  public String getResourceType() {
    return resourceType;
  }

  public UUID getResourceId() {
    return resourceId;
  }

  public Object getBeforeState() {
    return beforeState;
  }

  public Object getAfterState() {
    return afterState;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private UUID tenantId;

    private UUID actorUserId;

    private String action;

    private String resourceType;

    private UUID resourceId;

    private Object beforeState;

    private Object afterState;

    private String ipAddress;

    private String userAgent;

    private Instant occurredAt;

    private Builder() {}

    public Builder tenantId(UUID tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder actorUserId(UUID actorUserId) {
      this.actorUserId = actorUserId;
      return this;
    }

    public Builder action(String action) {
      this.action = action;
      return this;
    }

    public Builder resourceType(String resourceType) {
      this.resourceType = resourceType;
      return this;
    }

    public Builder resourceId(UUID resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    public Builder beforeState(Object beforeState) {
      this.beforeState = beforeState;
      return this;
    }

    public Builder afterState(Object afterState) {
      this.afterState = afterState;
      return this;
    }

    public Builder ipAddress(String ipAddress) {
      this.ipAddress = ipAddress;
      return this;
    }

    public Builder userAgent(String userAgent) {
      this.userAgent = userAgent;
      return this;
    }

    public Builder occurredAt(Instant occurredAt) {
      this.occurredAt = occurredAt;
      return this;
    }

    public AuditLog build() {
      if (tenantId == null) {
        throw new IllegalStateException("tenantId is required");
      }
      if (action == null || action.isBlank()) {
        throw new IllegalStateException("action is required");
      }
      if (resourceType == null || resourceType.isBlank()) {
        throw new IllegalStateException("resourceType is required");
      }
      return new AuditLog(this);
    }
  }
}
