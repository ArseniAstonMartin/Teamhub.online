package com.teamhub.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import com.teamhub.auth.UserRole;
import com.teamhub.config.TestcontainersConfiguration;
import com.teamhub.customer.Customer;
import com.teamhub.customer.CustomerRepository;
import com.teamhub.storage.dto.CompleteMultipartRequest;
import com.teamhub.storage.dto.CompletedPartInfo;
import com.teamhub.storage.dto.StartMultipartRequest;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
import org.springframework.context.annotation.Import;
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
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

/**
 * Integration tests for multipart photo upload (PRD §14.4; TASK-050).
 *
 * <ul>
 *   <li><b>Step 1</b> ({@link #step1_startUpload_uploadParts_complete()}): Start upload, get 3 part
 *       URLs, upload 3 parts via S3Client, complete — assert {@code job_photos} and {@code
 *       outbox_events} rows created.
 *   <li><b>Step 2</b> ({@link #step2_abortAndRestart_newUploadIdAndDifferentObjectKey()}): Abort
 *       after part 2; start a new upload — verify new {@code uploadId} and a fresh {@code
 *       objectKey}. Also tests re-fetching part URLs for an existing {@code IN_PROGRESS} upload.
 *   <li><b>Step 3</b> ({@link #step3_cleanupJob_abortsStaleUpload()}): 24h cleanup is tested by
 *       calling {@link MultipartUploadService#abortStale} with a mocked repository + S3Client.
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MultipartUploadIT {

  private static final String JOBS_URL = "/api/v1/jobs";

  private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

  /** Each part must be at least 5 MB for S3/MinIO multipart uploads (except the last part). */
  private static final int PART_SIZE_BYTES = 5 * 1024 * 1024;

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

  @Autowired private MultipartUploadRepository multipartUploadRepository;

  @Autowired private MultipartUploadService multipartUploadService;

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
                    "Multipart Upload Co " + UUID.randomUUID(),
                    "multipart+" + UUID.randomUUID() + "@test.com"));

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
              c.setName("Multipart Test Customer");
              c.setPhone("+15039998877");
              c.setAddressLine("2 Multipart Ave");
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
          jdbc.update("DELETE FROM multipart_uploads WHERE tenant_id = ?", tid);
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
  // Step 1 — Start upload, upload 3 parts, complete
  // =========================================================================

  /**
   * TASK-050 Step 1: Dispatcher starts a multipart upload, fetches part URLs for parts 1-3, uses
   * {@code S3Client.uploadPart} to upload each part, then completes the upload. Asserts a {@code
   * job_photos} row and a {@code PhotoUploaded} outbox event are created.
   */
  @Test
  void step1_startUpload_uploadParts_complete() throws Exception {
    UUID tid = tenant.getId();

    // Start upload
    StartMultipartRequest startReq = new StartMultipartRequest("image/jpeg", "jpg");
    MvcResult startResult =
        mockMvc
            .perform(
                post(JOBS_URL + "/" + jobId + "/photo-multipart/start")
                    .header("Authorization", "Bearer " + dispatcherToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(startReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.uploadId").isNotEmpty())
            .andExpect(jsonPath("$.objectKey").isNotEmpty())
            .andReturn();

    JsonNode startBody = objectMapper.readTree(startResult.getResponse().getContentAsString());
    String uploadId = startBody.get("uploadId").asText();
    String objectKey = startBody.get("objectKey").asText();

    assertThat(objectKey).startsWith(tid + "/jobs/" + jobId + "/");
    assertThat(objectKey).endsWith(".jpg");

    // Fetch part URLs and upload each part via S3Client (MinIO allows small parts)
    String[] eTags = new String[3];
    for (int partNum = 1; partNum <= 3; partNum++) {
      final int finalPartNum = partNum;
      mockMvc
          .perform(
              post(JOBS_URL + "/" + jobId + "/photo-multipart/" + uploadId + "/part/" + partNum)
                  .header("Authorization", "Bearer " + dispatcherToken))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.uploadUrl").isNotEmpty())
          .andExpect(jsonPath("$.partNumber").value(partNum))
          .andReturn();

      // Extract s3UploadId from the DB so we can call uploadPart directly
      String s3UploadId =
          jdbc.queryForObject(
              "SELECT s3_upload_id FROM multipart_uploads WHERE id = ?::uuid",
              String.class,
              uploadId);

      UploadPartResponse partResponse =
          s3Client.uploadPart(
              r ->
                  r.bucket(storageProperties.bucket())
                      .key(objectKey)
                      .uploadId(s3UploadId)
                      .partNumber(finalPartNum),
              RequestBody.fromBytes(new byte[PART_SIZE_BYTES]));

      assertThat(partResponse.sdkHttpResponse().statusCode()).isEqualTo(200);
      eTags[partNum - 1] = partResponse.eTag();
    }

    // Complete upload
    List<CompletedPartInfo> parts =
        List.of(
            new CompletedPartInfo(1, eTags[0]),
            new CompletedPartInfo(2, eTags[1]),
            new CompletedPartInfo(3, eTags[2]));
    CompleteMultipartRequest completeReq = new CompleteMultipartRequest(parts);

    mockMvc
        .perform(
            post(JOBS_URL + "/" + jobId + "/photo-multipart/" + uploadId + "/complete")
                .header("Authorization", "Bearer " + dispatcherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(completeReq)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.jobId").value(jobId.toString()))
        .andExpect(jsonPath("$.objectKey").value(objectKey));

    // Assert job_photos row
    Integer photoCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM job_photos WHERE job_id = ?::uuid AND tenant_id = ?",
            Integer.class,
            jobId,
            tid);
    assertThat(photoCount).isEqualTo(1);

    // Assert PhotoUploaded outbox event
    Integer outboxCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM outbox_events"
                + " WHERE tenant_id = ? AND event_type = 'PhotoUploaded' AND aggregate_id = ?::uuid",
            Integer.class,
            tid,
            jobId);
    assertThat(outboxCount).isEqualTo(1);

    // Assert multipart_uploads row is COMPLETED
    String status =
        jdbc.queryForObject(
            "SELECT status FROM multipart_uploads WHERE id = ?::uuid", String.class, uploadId);
    assertThat(status).isEqualTo("COMPLETED");
  }

  // =========================================================================
  // Step 2 — Abort after part 2, restart, verify new uploadId + objectKey
  // =========================================================================

  /**
   * TASK-050 Step 2: Start an upload, abort it after 2 parts, then start a new upload. Asserts the
   * new upload has a different {@code uploadId} and a different {@code objectKey} (content
   * addressed by UUID). Also verifies that re-fetching part URLs for an existing {@code
   * IN_PROGRESS} upload returns valid URLs for both part 1 and part 3.
   */
  @Test
  void step2_abortAndRestart_newUploadIdAndDifferentObjectKey() throws Exception {
    // Start first upload
    StartMultipartRequest startReq = new StartMultipartRequest("image/jpeg", "jpg");
    MvcResult firstStart =
        mockMvc
            .perform(
                post(JOBS_URL + "/" + jobId + "/photo-multipart/start")
                    .header("Authorization", "Bearer " + dispatcherToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(startReq)))
            .andExpect(status().isCreated())
            .andReturn();

    JsonNode firstBody = objectMapper.readTree(firstStart.getResponse().getContentAsString());
    String firstUploadId = firstBody.get("uploadId").asText();
    String firstObjectKey = firstBody.get("objectKey").asText();

    // Verify we can re-fetch part URLs for parts 1 and 3 on the same IN_PROGRESS upload
    for (int partNum : new int[] {1, 3}) {
      mockMvc
          .perform(
              post(JOBS_URL
                      + "/"
                      + jobId
                      + "/photo-multipart/"
                      + firstUploadId
                      + "/part/"
                      + partNum)
                  .header("Authorization", "Bearer " + dispatcherToken))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.uploadUrl").isNotEmpty())
          .andExpect(jsonPath("$.partNumber").value(partNum));
    }

    // Abort the first upload
    mockMvc
        .perform(
            delete(JOBS_URL + "/" + jobId + "/photo-multipart/" + firstUploadId)
                .header("Authorization", "Bearer " + dispatcherToken))
        .andExpect(status().isNoContent());

    // Verify status = ABORTED
    String abortedStatus =
        jdbc.queryForObject(
            "SELECT status FROM multipart_uploads WHERE id = ?::uuid", String.class, firstUploadId);
    assertThat(abortedStatus).isEqualTo("ABORTED");

    // Start a new upload
    MvcResult secondStart =
        mockMvc
            .perform(
                post(JOBS_URL + "/" + jobId + "/photo-multipart/start")
                    .header("Authorization", "Bearer " + dispatcherToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(startReq)))
            .andExpect(status().isCreated())
            .andReturn();

    JsonNode secondBody = objectMapper.readTree(secondStart.getResponse().getContentAsString());
    String secondUploadId = secondBody.get("uploadId").asText();
    String secondObjectKey = secondBody.get("objectKey").asText();

    assertThat(secondUploadId).isNotEqualTo(firstUploadId);
    assertThat(secondObjectKey).isNotEqualTo(firstObjectKey);
  }

  // =========================================================================
  // Step 3 — 24h cleanup aborts stale uploads
  // =========================================================================

  /**
   * TASK-050 Step 3: The 24-hour cleanup is tested by directly calling {@link
   * MultipartUploadService#abortStale} with mocked collaborators. Verifies that {@code
   * abortMultipartUpload} is called on S3 and {@code status} is set to {@code ABORTED}.
   */
  @Test
  void step3_cleanupJob_abortsStaleUpload() {
    MultipartUploadRepository mockRepo = mock(MultipartUploadRepository.class);
    S3Client mockS3 = mock(S3Client.class);

    MultipartUpload staleUpload =
        MultipartUpload.builder()
            .jobId(jobId)
            .tenantId(tenant.getId())
            .initiatedBy(UUID.randomUUID())
            .s3UploadId("stale-s3-upload-id")
            .objectKey(tenant.getId() + "/jobs/" + jobId + "/stale.jpg")
            .status("IN_PROGRESS")
            .partCount(0)
            .build();

    when(mockS3.abortMultipartUpload(
            any(software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest.class)))
        .thenReturn(
            software.amazon.awssdk.services.s3.model.AbortMultipartUploadResponse.builder()
                .build());
    when(mockRepo.save(any())).thenReturn(staleUpload);

    MultipartUploadService serviceUnderTest =
        new MultipartUploadService(mockS3, null, storageProperties, mockRepo, null, null);

    serviceUnderTest.abortStale(staleUpload);

    verify(mockS3)
        .abortMultipartUpload(
            any(software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest.class));
    assertThat(staleUpload.getStatus()).isEqualTo("ABORTED");
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
