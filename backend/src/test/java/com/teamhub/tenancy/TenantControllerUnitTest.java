package com.teamhub.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamhub.shared.TenantContext;
import com.teamhub.tenancy.dto.TenantResponse;
import com.teamhub.tenancy.dto.UpdateTenantRequest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Unit tests for {@link TenantController} (TASK-128). */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class TenantControllerUnitTest {

  @Mock private TenantService tenantService;

  private TenantController controller() {
    return new TenantController(tenantService);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void getTenant_delegatesToService() {
    UUID tenantId = UUID.randomUUID();
    TenantContext.set(tenantId);
    TenantResponse expected =
        new TenantResponse(tenantId, "Acme Plumbing", Plan.FREE, TenantTimezones.DEFAULT);
    when(tenantService.getTenant(tenantId)).thenReturn(expected);

    assertThat(controller().getTenant()).isEqualTo(expected);
  }

  @Test
  void updateTenant_delegatesToServiceWithActor() {
    UUID tenantId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    TenantContext.set(tenantId);
    TenantResponse expected =
        new TenantResponse(tenantId, "New Name", Plan.FREE, TenantTimezones.DEFAULT);
    when(tenantService.updateName(tenantId, "New Name", actorId)).thenReturn(expected);

    Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(actorId.toString()).build();
    JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);

    TenantResponse response = controller().updateTenant(new UpdateTenantRequest("New Name"), auth);

    assertThat(response).isEqualTo(expected);
    verify(tenantService).updateName(tenantId, "New Name", actorId);
  }
}
