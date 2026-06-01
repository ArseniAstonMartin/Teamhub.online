package com.teamhub.eventing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Time-scheduled work item backed by a Redis sorted set (PRD §22.5.4).
 *
 * <p>The dispatcher worker polls {@code ZRANGEBYSCORE delayed_jobs 0 <now> LIMIT 0 100} every 5
 * seconds. For each due entry it atomically removes it from the sorted set, sets this row's status
 * to {@code RUNNING}, executes the handler, then sets status to {@code DONE} (or {@code FAILED}
 * with retry). Cancellation is modelled by setting status to {@code CANCELLED}; the dispatcher
 * skips entries whose Postgres row is not {@code PENDING} at pick-up time.
 *
 * <p>Redis only stores the schedule ({@code run_at} as a Unix-ms score); Postgres stores the truth
 * and survives a Redis flush.
 */
@Entity
@Table(name = "delayed_jobs")
public class DelayedJob {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "kind", nullable = false)
  private String kind;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
  private String payload;

  @Column(name = "run_at", nullable = false)
  private Instant runAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private DelayedJobStatus status;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  protected DelayedJob() {}

  private DelayedJob(Builder builder) {
    this.tenantId = builder.tenantId;
    this.kind = builder.kind;
    this.payload = builder.payload;
    this.runAt = builder.runAt;
    this.status = DelayedJobStatus.PENDING;
    this.attemptCount = 0;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public String getKind() {
    return kind;
  }

  public String getPayload() {
    return payload;
  }

  public Instant getRunAt() {
    return runAt;
  }

  public DelayedJobStatus getStatus() {
    return status;
  }

  public int getAttemptCount() {
    return attemptCount;
  }

  public void setStatus(DelayedJobStatus status) {
    this.status = status;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private UUID tenantId;

    private String kind;

    private String payload;

    private Instant runAt;

    private Builder() {}

    public Builder tenantId(UUID tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder kind(String kind) {
      this.kind = kind;
      return this;
    }

    public Builder payload(String payload) {
      this.payload = payload;
      return this;
    }

    public Builder runAt(Instant runAt) {
      this.runAt = runAt;
      return this;
    }

    public DelayedJob build() {
      if (tenantId == null) {
        throw new IllegalStateException("tenantId is required");
      }
      if (kind == null || kind.isBlank()) {
        throw new IllegalStateException("kind is required");
      }
      if (payload == null) {
        throw new IllegalStateException("payload is required");
      }
      if (runAt == null) {
        throw new IllegalStateException("runAt is required");
      }
      return new DelayedJob(this);
    }
  }
}
