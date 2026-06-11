package com.teamhub.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.teamhub.billing.dto.BillingStatusResponse;
import com.teamhub.tenancy.Plan;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Unit tests for {@link BillingController#getStatus} (TASK-125). */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class BillingControllerUnitTest {

  @Mock private BillingService billingService;

  @Mock private BillingStatusService billingStatusService;

  @Mock private WebhookHandlerService webhookHandlerService;

  private BillingController controller() {
    return new BillingController(billingService, billingStatusService, webhookHandlerService);
  }

  @Test
  void getStatus_returnsBillingSnapshot() {
    UUID tenantId = UUID.randomUUID();
    BillingStatusResponse status =
        new BillingStatusResponse(
            Plan.FREE, Instant.parse("2026-07-01T00:00:00Z"), false, null, 3, 2);
    when(billingStatusService.getStatus(tenantId)).thenReturn(status);

    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(UUID.randomUUID().toString())
            .claim("tenant_id", tenantId.toString())
            .build();

    ResponseEntity<BillingStatusResponse> response =
        controller().getStatus(new JwtAuthenticationToken(jwt));

    assertThat(response.getBody()).isEqualTo(status);
  }
}
