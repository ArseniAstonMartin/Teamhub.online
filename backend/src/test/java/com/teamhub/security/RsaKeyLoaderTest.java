package com.teamhub.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.RSAKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies RS256 key loading from PEM paths and the ephemeral fallback (PRD §6.2, TASK-019). */
class RsaKeyLoaderTest {

  @Test
  void loadsKeypairFromPemPaths(@TempDir Path dir) throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair pair = generator.generateKeyPair();

    Path privatePem = dir.resolve("private.pem");
    Path publicPem = dir.resolve("public.pem");
    Files.writeString(privatePem, pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
    Files.writeString(publicPem, pem("PUBLIC KEY", pair.getPublic().getEncoded()));

    JwtProperties props =
        new JwtProperties(
            privatePem.toString(), publicPem.toString(), "test-kid-1", null, null, null);

    RSAKey key = RsaKeyLoader.load(props);

    assertThat(key.getKeyID()).isEqualTo("test-kid-1");
    assertThat(key.isPrivate()).isTrue();
    assertThat(key.toRSAPublicKey()).isEqualTo(pair.getPublic());
  }

  @Test
  void generatesEphemeralKeypairWhenNoPaths() {
    JwtProperties props = new JwtProperties(null, null, null, null, null, null);

    RSAKey key = RsaKeyLoader.load(props);

    assertThat(key.isPrivate()).isTrue();
    assertThat(key.getKeyID()).isNotBlank();
  }

  @Test
  void propertiesApplyDefaults() {
    JwtProperties props = new JwtProperties(null, null, null, null, null, null);

    assertThat(props.issuer()).isEqualTo("https://app.teamhub.online");
    assertThat(props.accessTokenTtl()).isEqualTo(Duration.ofMinutes(15));
    assertThat(props.refreshTokenTtl()).isEqualTo(Duration.ofDays(30));
    assertThat(props.hasKeyPaths()).isFalse();
  }

  private static String pem(String type, byte[] der) {
    String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
    return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
  }
}
