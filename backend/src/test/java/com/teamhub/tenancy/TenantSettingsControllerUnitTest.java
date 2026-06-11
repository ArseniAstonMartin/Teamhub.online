package com.teamhub.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamhub.shared.TenantContext;
import com.teamhub.tenancy.dto.TenantSettingsResponse;
import com.teamhub.tenancy.dto.UpdateTenantSettingsRequest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Unit tests for {@link TenantSettingsController} (TASK-128). */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class TenantSettingsControllerUnitTest {

  @Mock private TenantSettingsService tenantSettingsService;

  private TenantSettingsController controller() {
    return new TenantSettingsController(tenantSettingsService);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void getSettings_delegatesToService() {
    UUID tenantId = UUID.randomUUID();
    TenantContext.set(tenantId);
    TenantSettingsResponse expected = new TenantSettingsResponse(true, TenantTimezones.DEFAULT);
    when(tenantSettingsService.getSettings(tenantId)).thenReturn(expected);

    assertThat(controller().getSettings()).isEqualTo(expected);
  }

  @Test
  void updateSettings_appliesTimezoneWhenProvided() {
    UUID tenantId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    TenantContext.set(tenantId);
    TenantSettingsResponse current = new TenantSettingsResponse(false, TenantTimezones.DEFAULT);
    TenantSettingsResponse updated = new TenantSettingsResponse(false, "America/Los_Angeles");

    when(tenantSettingsService.getSettings(tenantId)).thenReturn(current);
    when(tenantSettingsService.setTimezone(tenantId, "America/Los_Angeles", actorId))
        .thenReturn(updated);

    Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(actorId.toString()).build();
    JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);

    TenantSettingsResponse response =
        controller()
            .updateSettings(new UpdateTenantSettingsRequest(null, "America/Los_Angeles"), auth);

    assertThat(response.timezone()).isEqualTo("America/Los_Angeles");
    verify(tenantSettingsService).setTimezone(tenantId, "America/Los_Angeles", actorId);
  }

  @Test
  void updateSettings_appliesCloseJobsAndTimezone() {
    UUID tenantId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    TenantContext.set(tenantId);
    TenantSettingsResponse current = new TenantSettingsResponse(false, TenantTimezones.DEFAULT);
    TenantSettingsResponse afterClose = new TenantSettingsResponse(true, TenantTimezones.DEFAULT);
    TenantSettingsResponse afterTimezone = new TenantSettingsResponse(true, "America/Chicago");

    when(tenantSettingsService.getSettings(tenantId)).thenReturn(current);
    when(tenantSettingsService.setCloseJobsEnabled(tenantId, true, actorId)).thenReturn(afterClose);
    when(tenantSettingsService.setTimezone(tenantId, "America/Chicago", actorId))
        .thenReturn(afterTimezone);

    Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(actorId.toString()).build();
    JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);

    TenantSettingsResponse response =
        controller().updateSettings(new UpdateTenantSettingsRequest(true, "America/Chicago"), auth);

    assertThat(response.closeJobsEnabled()).isTrue();
    assertThat(response.timezone()).isEqualTo("America/Chicago");
    verify(tenantSettingsService).setCloseJobsEnabled(tenantId, true, actorId);
    verify(tenantSettingsService).setTimezone(tenantId, "America/Chicago", actorId);
  }
}
