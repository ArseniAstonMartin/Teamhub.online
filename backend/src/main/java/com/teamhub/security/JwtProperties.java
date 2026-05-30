package com.teamhub.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT configuration (PRD §6.2, §24.4).
 *
 * <p>RS256 signing keys are loaded from the PEM file paths given by {@code privateKeyPath} / {@code
 * publicKeyPath} (wired from {@code JWT_PRIVATE_KEY_PATH} / {@code JWT_PUBLIC_KEY_PATH} environment
 * variables). When both paths are blank an ephemeral in-memory keypair is generated at startup —
 * convenient for local development and tests, never intended for production where the keypair must
 * be persistent and rotated.
 *
 * @param privateKeyPath filesystem path to the PKCS#8 PEM RSA private key, or blank to generate
 * @param publicKeyPath filesystem path to the X.509 PEM RSA public key, or blank to generate
 * @param kid key id stamped into the token header; supports rotation. Blank → derived thumbprint
 * @param issuer the {@code iss} claim value
 * @param accessTokenTtl access-token lifetime (PRD: 15 minutes)
 * @param refreshTokenTtl refresh-token lifetime (PRD: 30 days, rolling)
 */
@ConfigurationProperties(prefix = "teamhub.jwt")
public record JwtProperties(
    String privateKeyPath,
    String publicKeyPath,
    String kid,
    String issuer,
    Duration accessTokenTtl,
    Duration refreshTokenTtl) {

  public JwtProperties {
    if (issuer == null || issuer.isBlank()) {
      issuer = "https://app.teamhub.online";
    }
    if (accessTokenTtl == null) {
      accessTokenTtl = Duration.ofMinutes(15);
    }
    if (refreshTokenTtl == null) {
      refreshTokenTtl = Duration.ofDays(30);
    }
  }

  /** True when both key paths are configured, i.e. keys should be loaded from disk. */
  public boolean hasKeyPaths() {
    return privateKeyPath != null
        && !privateKeyPath.isBlank()
        && publicKeyPath != null
        && !publicKeyPath.isBlank();
  }
}
