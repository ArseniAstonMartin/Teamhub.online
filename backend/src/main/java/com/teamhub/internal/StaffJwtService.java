package com.teamhub.internal;

import com.nimbusds.jose.jwk.RSAKey;
import com.teamhub.security.JwtProperties;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues RS256 access tokens for Teamhub staff (PRD §6.4, TASK-131).
 *
 * <p>Staff tokens carry {@code sub} and {@code role=TEAMHUB_STAFF} only — no {@code tenant_id}
 * claim.
 */
@Service
public class StaffJwtService {

  public static final String STAFF_ROLE_CLAIM = StaffRole.TEAMHUB_STAFF.name();

  private final JwtEncoder jwtEncoder;

  private final JwtProperties properties;

  private final String kid;

  public StaffJwtService(JwtEncoder jwtEncoder, JwtProperties properties, RSAKey jwtSigningKey) {
    this.jwtEncoder = jwtEncoder;
    this.properties = properties;
    this.kid = jwtSigningKey.getKeyID();
  }

  /** Result of issuing a staff access token. */
  public record StaffAccessToken(String value, Instant expiresAt) {}

  /**
   * Issues a signed staff access token.
   *
   * @param staff the authenticated staff user
   * @return the access token and its expiry
   */
  public StaffAccessToken issue(StaffUser staff) {
    Instant now = Instant.now();
    Instant expiresAt = now.plus(properties.accessTokenTtl());

    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .subject(staff.getId().toString())
            .claim("role", STAFF_ROLE_CLAIM)
            .build();

    JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(kid).build();

    return new StaffAccessToken(
        jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), expiresAt);
  }
}
