package com.teamhub.auth;

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
 * Issues short-lived RS256 access tokens (PRD §6.2).
 *
 * <p>Token payload contains {@code sub} (user id), {@code tenant_id}, {@code role}, {@code exp}
 * (issued + 15 min), {@code iat}, and a {@code kid} header identifying the signing key.
 */
@Service
public class JwtService {

  private final JwtEncoder jwtEncoder;

  private final JwtProperties properties;

  private final String kid;

  public JwtService(JwtEncoder jwtEncoder, JwtProperties properties, RSAKey jwtSigningKey) {
    this.jwtEncoder = jwtEncoder;
    this.properties = properties;
    this.kid = jwtSigningKey.getKeyID();
  }

  /** Result of issuing an access token: the compact JWT and the instant it expires. */
  public record AccessToken(String value, Instant expiresAt) {}

  /**
   * Issues a signed access token for the given user.
   *
   * @param user the authenticated user
   * @return the access token and its expiry
   */
  public AccessToken issue(User user) {
    Instant now = Instant.now();
    Instant expiresAt = now.plus(properties.accessTokenTtl());

    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .subject(user.getId().toString())
            .claim("tenant_id", user.getTenant().getId().toString())
            .claim("role", user.getRole().name())
            .build();

    JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(kid).build();

    return new AccessToken(
        jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), expiresAt);
  }
}
