package com.teamhub.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.teamhub.security.JwtProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class StaffJwtServiceTest {

  @Mock private JwtEncoder jwtEncoder;

  private StaffJwtService staffJwtService;

  @BeforeEach
  void setUp() throws Exception {
    RSAKey rsaKey = new RSAKeyGenerator(2048).keyID("test-kid").generate();
    JwtProperties properties =
        new JwtProperties(
            null,
            null,
            "test-kid",
            "https://app.teamhub.online",
            Duration.ofMinutes(15),
            Duration.ofDays(30));
    staffJwtService = new StaffJwtService(jwtEncoder, properties, rsaKey);
  }

  @Test
  void issue_returnsTokenWithoutTenantIdClaim() {
    StaffUser staff = new StaffUser("dev@teamhub.online", "hash", "Dev", StaffRole.TEAMHUB_STAFF);
    staff.setId(java.util.UUID.randomUUID());

    when(jwtEncoder.encode(any(JwtEncoderParameters.class)))
        .thenAnswer(
            invocation -> {
              JwtEncoderParameters params = invocation.getArgument(0);
              String token = params.getJwsHeader().getKeyId() + ".payload.sig";
              return Jwt.withTokenValue(token)
                  .header("alg", "RS256")
                  .claim("role", StaffJwtService.STAFF_ROLE_CLAIM)
                  .subject(staff.getId().toString())
                  .build();
            });

    StaffJwtService.StaffAccessToken result = staffJwtService.issue(staff);

    assertThat(result.value()).isNotBlank();
    assertThat(result.expiresAt()).isAfter(java.time.Instant.now());
  }
}
