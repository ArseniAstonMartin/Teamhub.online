package com.teamhub.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Loads an RS256 signing keypair from PEM files, or generates an ephemeral one when no paths are
 * configured (PRD §6.2, §24.4).
 *
 * <p>The resulting {@link RSAKey} carries both the private and public parts plus a {@code kid}, so
 * a single value drives both token issuance (private) and verification key selection (public). Key
 * rotation is supported by minting tokens with the current key's {@code kid} while the decoder can
 * verify against a JWK set keyed by {@code kid}.
 */
final class RsaKeyLoader {

  private RsaKeyLoader() {}

  /**
   * Builds the signing {@link RSAKey} from the given properties, loading or generating as needed.
   */
  static RSAKey load(JwtProperties props) {
    if (props.hasKeyPaths()) {
      RSAPrivateKey privateKey = readPrivateKey(props.privateKeyPath());
      RSAPublicKey publicKey = readPublicKey(props.publicKeyPath());
      return build(publicKey, privateKey, props.kid());
    }
    return generate(props.kid());
  }

  private static RSAKey build(RSAPublicKey publicKey, RSAPrivateKey privateKey, String kid) {
    RSAKey.Builder builder = new RSAKey.Builder(publicKey).privateKey(privateKey);
    try {
      if (kid != null && !kid.isBlank()) {
        builder.keyID(kid);
      } else {
        // Deterministic thumbprint kid so independent instances loading the same key agree.
        builder.keyIDFromThumbprint();
      }
      return builder.build();
    } catch (JOSEException e) {
      throw new IllegalStateException("Unable to compute key thumbprint", e);
    }
  }

  private static RSAKey generate(String kid) {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KeyPair pair = generator.generateKeyPair();
      return build((RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate(), kid);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("RSA key generation failed", e);
    }
  }

  private static RSAPrivateKey readPrivateKey(String path) {
    byte[] der = decodePem(path, "PRIVATE KEY");
    try {
      KeyFactory factory = KeyFactory.getInstance("RSA");
      return (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(der));
    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new IllegalStateException("Invalid RSA private key at " + path, e);
    }
  }

  private static RSAPublicKey readPublicKey(String path) {
    byte[] der = decodePem(path, "PUBLIC KEY");
    try {
      KeyFactory factory = KeyFactory.getInstance("RSA");
      return (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(der));
    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new IllegalStateException("Invalid RSA public key at " + path, e);
    }
  }

  private static byte[] decodePem(String path, String type) {
    String pem;
    try {
      pem = Files.readString(Path.of(path));
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read key file: " + path, e);
    }
    String base64 =
        pem.replace("-----BEGIN " + type + "-----", "")
            .replace("-----END " + type + "-----", "")
            .replaceAll("\\s", "");
    return Base64.getDecoder().decode(base64);
  }
}
