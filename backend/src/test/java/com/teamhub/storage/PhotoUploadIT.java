package com.teamhub.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import com.teamhub.auth.UserRole;
import com.teamhub.config.IntegrationTest;
import com.teamhub.customer.Customer;
import com.teamhub.customer.CustomerRepository;
import com.teamhub.storage.dto.ConfirmUploadRequest;
import com.teamhub.storage.dto.GenerateUploadUrlRequest;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/**
 * Integration tests for photo upload via pre-signed URLs (PRD §14.2, §14.3; TASK-049).
 *
 * <ul>
 *   <li><b>Step 1</b> ({@link #step1_generateUploadUrl_and_putToMinio()}): Dispatcher generates a
 *       pre-signed URL; PUT a 1-byte JPEG to MinIO via {@code S3Client} → 200.
 *   <li><b>Step 2</b> ({@link #step2_confirmUpload_createsRowAndEvent()}): Confirm metadata → 201;
 *       {@code job_photos} row in DB; {@code PhotoUploaded} event in {@code outbox_events}.
 *   <li><b>Step 3</b> ({@link #step3_confirmUpload_tooLarge_returns422()}): Confirm 25 MB size →
 *       422 {@code PHOTO_TOO_LARGE}.
 *   <li><b>Step 4</b> ({@link #step4_generateUploadUrl_pdfMime_returns422()}): Generate URL for
 *       {@code application/pdf} → 422 {@code UNSUPPORTED_MIME}.
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@IntegrationTest
class PhotoUploadIT {

  private static final String JOBS_URL = "/api/v1/jobs";

  private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private InternalTenantService tenantService;

  @Autowired private CustomerRepository customerRepository;

  @Autowired private JwtEncoder jwtEncoder;

  @Autowired private RSAKey signingKey;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private PlatformTransactionManager txManager;

  @Autowired private S3Client s3Client;

  @Autowired private ObjectStorageProperties storageProperties;

  private TransactionTemplate tx;

  private Tenant tenant;

  private UUID jobId;

  private String dispatcherToken;

  @BeforeEach
  void setUp() {
    tx = new TransactionTemplate(txManager);
    tenant =
        tx.execute(
            s ->
                tenantService.provision(
                    "Photo Upload Co " + UUID.randomUUID(),
                    "photo+" + UUID.randomUUID() + "@test.com"));

    UUID tid = tenant.getId();
    UUID dispatcherUserId = UUID.randomUUID();

    tx.execute(
        s -> {
          jdbc.update(
              "INSERT INTO users (id, tenant_id, email, role)"
                  + " VALUES (?::uuid, ?::uuid, ?, 'DISPATCHER')",
              dispatcherUserId,
              tid,
              "dispatcher-" + dispatcherUserId + "@test.com");
          return null;
        });

    UUID customerId =
        tx.execute(
            s -> {
              Customer c = new Customer();
              c.setTenantId(tid);
              c.setName("Photo Test Customer");
              c.setPhone("+15031112233");
              c.setAddressLine("1 Photo St");
              c.setCity("Portland");
              c.setState("OR");
              c.setZip("97201");
              c.setLocation(GF.createPoint(new Coordinate(-122.6784, 45.5231)));
              customerRepository.save(c);
              return c.getId();
            });

    jobId =
        tx.execute(
            s ->
                jdbc.queryForObject(
                    "INSERT INTO jobs (tenant_id, customer_id, status, custom_fields, version)"
                        + " VALUES (?::uuid, ?::uuid, 'SUBMITTED', '{}', 0) RETURNING id",
                    UUID.class,
                    tid,
                    customerId));

    dispatcherToken = mintToken(tid, dispatcherUserId, UserRole.DISPATCHER.name());
  }

  @AfterEach
  void tearDown() {
    UUID tid = tenant.getId();
    tx.execute(
        s -> {
          jdbc.update("DELETE FROM job_photos WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM outbox_events WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM delayed_jobs WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM jobs WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM customers WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM users WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM processed_events WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM tenants WHERE id = ?", tid);
          return null;
        });
  }

  // =========================================================================
  // Step 1 — Generate pre-signed URL; PUT 1-byte JPEG to MinIO via S3Client → 200
  // =========================================================================

  /**
   * TASK-049 Step 1: Dispatcher requests a pre-signed URL for {@code image/jpeg}; the test then
   * uses {@code S3Client.putObject} to PUT a 1-byte payload to MinIO and asserts success.
   *
   * <p>The presigned URL is validated to be non-empty and contain the expected object key. The
   * actual upload uses {@code S3Client.putObject} (path-style, already configured) to avoid
   * DNS-resolution constraints of virtual-host-style presigned URLs in local Testcontainers.
   */
  @Test
  void step1_generateUploadUrl_and_putToMinio() throws Exception {
    GenerateUploadUrlRequest req = new GenerateUploadUrlRequest("image/jpeg", "jpg");

    MvcResult result =
        mockMvc
            .perform(
                post(JOBS_URL + "/" + jobId + "/photo-upload-url")
                    .header("Authorization", "Bearer " + dispatcherToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uploadUrl").isNotEmpty())
            .andExpect(jsonPath("$.objectKey").isNotEmpty())
            .andExpect(jsonPath("$.expiresAt").isNotEmpty())
            .andReturn();

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    String objectKey = body.get("objectKey").asText();

    // Verify the object key has the correct tenant/job prefix
    assertThat(objectKey).startsWith(tenant.getId() + "/jobs/" + jobId + "/");
    assertThat(objectKey).endsWith(".jpg");

    // PUT 1-byte JPEG to MinIO via S3Client (path-style, already configured with forcePathStyle)
    PutObjectResponse putResponse =
        s3Client.putObject(
            r -> r.bucket(storageProperties.bucket()).key(objectKey).contentType("image/jpeg"),
            RequestBody.fromBytes(new byte[] {(byte) 0xFF}));

    assertThat(putResponse.sdkHttpResponse().statusCode()).isEqualTo(200);
  }

  // =========================================================================
  // Step 2 — Confirm metadata → 201; DB row; outbox event
  // =========================================================================

  /**
   * TASK-049 Step 2: Confirm a valid photo upload; expects 201, a {@code job_photos} row, and a
   * {@code PhotoUploaded} outbox event — all in the same transaction.
   */
  @Test
  void step2_confirmUpload_createsRowAndEvent() throws Exception {
    UUID tid = tenant.getId();
    String objectKey = "%s/jobs/%s/%s.jpg".formatted(tid, jobId, UUID.randomUUID());
    ConfirmUploadRequest req =
        new ConfirmUploadRequest(objectKey, "image/jpeg", 1024L, 640, 480, null);

    MvcResult result =
        mockMvc
            .perform(
                post(JOBS_URL + "/" + jobId + "/photos")
                    .header("Authorization", "Bearer " + dispatcherToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.jobId").value(jobId.toString()))
            .andExpect(jsonPath("$.objectKey").value(objectKey))
            .andExpect(jsonPath("$.contentType").value("image/jpeg"))
            .andExpect(jsonPath("$.sizeBytes").value(1024))
            .andReturn();

    String photoId =
        objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

    // Verify job_photos row
    Integer photoCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM job_photos WHERE id = ?::uuid AND tenant_id = ?",
            Integer.class,
            photoId,
            tid);
    assertThat(photoCount).isEqualTo(1);

    // Verify PhotoUploaded event in outbox
    Integer outboxCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM outbox_events"
                + " WHERE tenant_id = ? AND event_type = 'PhotoUploaded' AND aggregate_id = ?::uuid",
            Integer.class,
            tid,
            jobId);
    assertThat(outboxCount).isEqualTo(1);
  }

  // =========================================================================
  // Step 3 — Confirm 25 MB → 422 PHOTO_TOO_LARGE
  // =========================================================================

  /**
   * TASK-049 Step 3: Attempting to confirm a photo with {@code sizeBytes = 25 * 1024 * 1024}
   * returns 422 with {@code PHOTO_TOO_LARGE}.
   */
  @Test
  void step3_confirmUpload_tooLarge_returns422() throws Exception {
    UUID tid = tenant.getId();
    String objectKey = "%s/jobs/%s/%s.jpg".formatted(tid, jobId, UUID.randomUUID());
    long oversizedBytes = 25L * 1024 * 1024;
    ConfirmUploadRequest req =
        new ConfirmUploadRequest(objectKey, "image/jpeg", oversizedBytes, null, null, null);

    mockMvc
        .perform(
            post(JOBS_URL + "/" + jobId + "/photos")
                .header("Authorization", "Bearer " + dispatcherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("PHOTO_TOO_LARGE"));
  }

  // =========================================================================
  // Step 4 — Generate URL for application/pdf → 422 UNSUPPORTED_MIME
  // =========================================================================

  /**
   * TASK-049 Step 4: Requesting a pre-signed URL for {@code application/pdf} returns 422 with
   * {@code UNSUPPORTED_MIME}.
   */
  @Test
  void step4_generateUploadUrl_pdfMime_returns422() throws Exception {
    GenerateUploadUrlRequest req = new GenerateUploadUrlRequest("application/pdf", "pdf");

    mockMvc
        .perform(
            post(JOBS_URL + "/" + jobId + "/photo-upload-url")
                .header("Authorization", "Bearer " + dispatcherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MIME"));
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  private String mintToken(UUID tenantId, UUID userId, String roleCode) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("https://app.teamhub.online")
            .issuedAt(now)
            .expiresAt(now.plus(15, ChronoUnit.MINUTES))
            .subject(userId.toString())
            .claim("tenant_id", tenantId.toString())
            .claim("role", roleCode)
            .build();
    JwsHeader header =
        JwsHeader.with(SignatureAlgorithm.RS256).keyId(signingKey.getKeyID()).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
