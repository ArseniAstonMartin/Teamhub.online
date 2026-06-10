package com.teamhub.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.teamhub.auth.UserRepository;
import com.teamhub.billing.dto.BillingStatusResponse;
import com.teamhub.tenancy.Plan;
import com.teamhub.tenancy.Tenant;
import com.teamhub.tenancy.TenantRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link BillingStatusService} (TASK-125). */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class BillingStatusServiceUnitTest {

  @Mock private TenantRepository tenantRepository;

  @Mock private TenantQuotaStateRepository quotaStateRepository;

  @Mock private UserRepository userRepository;

  private BillingStatusService service() {
    return new BillingStatusService(tenantRepository, quotaStateRepository, userRepository);
  }

  @Test
  void getStatus_aggregatesTenantQuotaAndSeats() {
    UUID tenantId = UUID.randomUUID();
    Instant periodEnd = Instant.parse("2026-07-01T00:00:00Z");
    Instant paymentFailedAt = Instant.parse("2026-06-01T00:00:00Z");

    Tenant tenant = new Tenant("Acme");
    tenant.setId(tenantId);
    tenant.setPlan(Plan.TEAM);
    tenant.setCurrentPeriodEnd(periodEnd);
    tenant.setPaymentFailedAt(paymentFailedAt);

    TenantQuotaState quotaState = new TenantQuotaState();
    quotaState.setJobCount(12);

    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(quotaStateRepository.findById(tenantId)).thenReturn(Optional.of(quotaState));
    when(userRepository.countByTenantIdAndDeactivatedAtIsNull(tenantId)).thenReturn(4L);

    BillingStatusResponse response = service().getStatus(tenantId);

    assertThat(response.plan()).isEqualTo(Plan.TEAM);
    assertThat(response.periodEnd()).isEqualTo(periodEnd);
    assertThat(response.paymentFailed()).isTrue();
    assertThat(response.paymentFailedAt()).isEqualTo(paymentFailedAt);
    assertThat(response.jobsThisPeriod()).isEqualTo(12);
    assertThat(response.seats()).isEqualTo(4);
  }

  @Test
  void getStatus_withoutQuotaState_usesZeroJobs() {
    UUID tenantId = UUID.randomUUID();
    Tenant tenant = new Tenant("Acme");
    tenant.setId(tenantId);

    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(quotaStateRepository.findById(tenantId)).thenReturn(Optional.empty());
    when(userRepository.countByTenantIdAndDeactivatedAtIsNull(tenantId)).thenReturn(1L);

    BillingStatusResponse response = service().getStatus(tenantId);

    assertThat(response.jobsThisPeriod()).isZero();
    assertThat(response.paymentFailed()).isFalse();
    assertThat(response.periodEnd()).isNotNull();
  }
}
