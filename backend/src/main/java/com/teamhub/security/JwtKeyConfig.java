package com.teamhub.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Wires the RS256 {@link JwtEncoder} (issuance) and {@link JwtDecoder} (validation) from the
 * configured signing key (PRD §6.2).
 *
 * <p>The decoder selects the verification key by the token's {@code kid} header against a JWK set,
 * so additional public keys can be added to the set during a rotation window without invalidating
 * tokens already signed by the previous key.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtKeyConfig {

  private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);

  /** The current signing key (private + public + kid). */
  @Bean
  RSAKey jwtSigningKey(JwtProperties props) {
    RSAKey key = RsaKeyLoader.load(props);
    if (props.hasKeyPaths()) {
      log.info("Loaded RS256 signing key from configured PEM paths (kid={})", key.getKeyID());
    } else {
      log.warn(
          "JWT_PRIVATE_KEY_PATH / JWT_PUBLIC_KEY_PATH not set — generated an EPHEMERAL RS256 keypair"
              + " (kid={}). Tokens will not survive a restart. Configure persistent keys for"
              + " production.",
          key.getKeyID());
    }
    return key;
  }

  @Bean
  JwtEncoder jwtEncoder(RSAKey jwtSigningKey) {
    JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(jwtSigningKey));
    return new NimbusJwtEncoder(jwkSource);
  }

  @Bean
  JwtDecoder jwtDecoder(RSAKey jwtSigningKey) {
    // Verify against the public half of every known key, selected by the token's kid header.
    JWKSource<SecurityContext> jwkSource =
        new ImmutableJWKSet<>(new JWKSet(jwtSigningKey.toPublicJWK()));
    DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
    processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
    return new NimbusJwtDecoder(processor);
  }
}
