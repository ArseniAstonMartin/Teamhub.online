package com.teamhub.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.teamhub.config.TestcontainersConfiguration;
import com.teamhub.customer.Customer;
import com.teamhub.customer.CustomerRepository;
import com.teamhub.schemas.AppointmentSchema;
import com.teamhub.schemas.FieldDefinition;
import com.teamhub.schemas.SchemaService;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for the {@code jobs} table, {@link Job} entity, {@link
 * JobCustomFieldValidator}, and optimistic locking (PRD §9, §10.2, TASK-042).
 *
 * <ul>
 *   <li>Step 1: insert a job with valid custom fields — succeeds.
 *   <li>Step 2: {@link JobCustomFieldValidator#validate} with a missing required field — throws
 *       {@link CustomFieldValidationException} (HTTP 422).
 *   <li>Step 3: two transactions load the same job; the second commit raises {@link
 *       ObjectOptimisticLockingFailureException}.
 * </ul>
 *
 * <p>Steps 1 and 2 are wrapped in a rolled-back {@code @Transactional} method (no cleanup needed).
 * Step 3 commits real transactions and cleans up in {@code @AfterEach}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JobIT {

  private static final String SCHEMA_NAME = "Test Job Schema";

  private static final String REQUIRED_FIELD_KEY = "vehicle_make";

  @Autowired private InternalTenantService tenantService;

  @Autowired private CustomerRepository customerRepository;

  @Autowired private SchemaService schemaService;

  @Autowired private JobRepository jobRepository;

  @Autowired private JobCustomFieldValidator validator;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private PlatformTransactionManager txManager;

  @PersistenceContext private EntityManager em;

  /** Tenant committed by Step 3 — cleaned up in {@link #tearDownStep3()}. */
  private UUID step3TenantId;

  @AfterEach
  void tearDownStep3() {
    if (step3TenantId == null) {
      return;
    }
    UUID tid = step3TenantId;
    step3TenantId = null;
    TransactionTemplate tx = new TransactionTemplate(txManager);
    tx.execute(
        s -> {
          jdbc.update("DELETE FROM jobs WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM customers WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM appointment_schemas WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM users WHERE tenant_id = ?", tid);
          jdbc.update("DELETE FROM tenants WHERE id = ?", tid);
          return null;
        });
  }

  // ---------------------------------------------------------------------------
  // Step 1 — Insert a job with valid custom fields: succeeds
  // ---------------------------------------------------------------------------

  /**
   * TASK-042 Test Step 1: Saving a {@link Job} whose {@code custom_fields} map contains a value for
   * every {@code required} field defined in the referenced schema must persist successfully.
   */
  @Test
  @Transactional
  void step1_insertJobWithValidCustomFields_persistsSuccessfully() {
    Tenant tenant = tenantService.provision("Job IT Step1", "job-step1@test.com");
    Customer customer = buildCustomer(tenant.getId());
    customerRepository.save(customer);
    em.flush();

    AppointmentSchema schema = publishRequiredFieldSchema(tenant.getId());

    Map<String, Object> fields = Map.of(REQUIRED_FIELD_KEY, "Ford");
    Job job = buildJob(tenant.getId(), customer.getId(), schema.getId(), fields);

    Job saved = jobRepository.save(job);
    em.flush();

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getVersion()).isEqualTo(0);
    assertThat(saved.getStatus()).isEqualTo(JobStatus.SUBMITTED);
    assertThat(saved.getCustomFields()).containsEntry(REQUIRED_FIELD_KEY, "Ford");
    assertThat(saved.getTenantId()).isEqualTo(tenant.getId());
    assertThat(saved.getCustomerId()).isEqualTo(customer.getId());
    assertThat(saved.getAppointmentSchemaId()).isEqualTo(schema.getId());
  }

  // ---------------------------------------------------------------------------
  // Step 2 — Missing required field: CustomFieldValidationException (422)
  // ---------------------------------------------------------------------------

  /**
   * TASK-042 Test Step 2: Calling {@link JobCustomFieldValidator#validate} when the submitted
   * {@code custom_fields} map is missing a required field must throw {@link
   * CustomFieldValidationException} (HTTP 422, code {@code CUSTOM_FIELD_VALIDATION_ERROR}).
   */
  @Test
  @Transactional
  void step2_missingRequiredCustomField_throwsCustomFieldValidationException() {
    Tenant tenant = tenantService.provision("Job IT Step2", "job-step2@test.com");
    AppointmentSchema schema = publishRequiredFieldSchema(tenant.getId());

    // Flush so schemaRepository.findById() within the validator can see the row.
    em.flush();

    assertThatThrownBy(() -> validator.validate(tenant.getId(), schema.getId(), Map.of()))
        .isInstanceOf(CustomFieldValidationException.class)
        .hasFieldOrPropertyWithValue(
            "httpStatus", org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY)
        .hasMessageContaining(REQUIRED_FIELD_KEY);
  }

  /**
   * Passing {@code null} for {@code customFields} is treated as an empty map — all required fields
   * are considered missing.
   */
  @Test
  @Transactional
  void step2_nullCustomFields_throwsCustomFieldValidationException() {
    Tenant tenant = tenantService.provision("Job IT Step2b", "job-step2b@test.com");
    AppointmentSchema schema = publishRequiredFieldSchema(tenant.getId());
    em.flush();

    assertThatThrownBy(() -> validator.validate(tenant.getId(), schema.getId(), null))
        .isInstanceOf(CustomFieldValidationException.class)
        .hasMessageContaining(REQUIRED_FIELD_KEY);
  }

  /**
   * {@link CustomFieldValidationException#CODE} must equal {@code CUSTOM_FIELD_VALIDATION_ERROR}.
   */
  @Test
  @Transactional
  void step2_exceptionCarriesCorrectErrorCode() {
    Tenant tenant = tenantService.provision("Job IT Step2c", "job-step2c@test.com");
    AppointmentSchema schema = publishRequiredFieldSchema(tenant.getId());
    em.flush();

    assertThatThrownBy(() -> validator.validate(tenant.getId(), schema.getId(), Map.of()))
        .isInstanceOf(CustomFieldValidationException.class)
        .satisfies(
            ex ->
                assertThat(((CustomFieldValidationException) ex).getCode())
                    .isEqualTo(CustomFieldValidationException.CODE));
  }

  // ---------------------------------------------------------------------------
  // Step 3 — Concurrent optimistic-lock test
  // ---------------------------------------------------------------------------

  /**
   * TASK-042 Test Step 3: Two transactions load the same {@link Job} row. The first commits an
   * update (version 0 → 1). The second transaction attempts to commit a conflicting update (still
   * at version 0) and must throw {@link ObjectOptimisticLockingFailureException}.
   *
   * <p>This covers PRD §10.2 "first technician wins" atomicity. This test commits real database
   * transactions; {@link #tearDownStep3()} cleans up the committed data afterwards.
   */
  @Test
  void step3_concurrentUpdate_secondCommitRaisesOptimisticLockException() {
    TransactionTemplate tx = new TransactionTemplate(txManager);

    // Arrange: create tenant, customer, schema in separate committed transactions.
    UUID tenantId =
        tx.execute(s -> tenantService.provision("Job IT Step3", "job-step3@test.com").getId());
    step3TenantId = tenantId;

    UUID customerId =
        tx.execute(
            s -> {
              Customer c = buildCustomer(tenantId);
              customerRepository.save(c);
              return c.getId();
            });

    UUID schemaId = tx.execute(s -> publishRequiredFieldSchema(tenantId).getId());

    // TX1: save job (version = 0).
    UUID jobId =
        tx.execute(
            s -> {
              Job j =
                  buildJob(tenantId, customerId, schemaId, Map.of(REQUIRED_FIELD_KEY, "Toyota"));
              jobRepository.save(j);
              return j.getId();
            });

    // Load into two independent detached copies (each load uses its own read TX).
    Job copy1 = tx.execute(s -> jobRepository.findById(jobId).orElseThrow());
    Job copy2 = tx.execute(s -> jobRepository.findById(jobId).orElseThrow());

    // TX2: copy1 commits first — version increments 0 → 1.
    tx.execute(
        s -> {
          copy1.setStatus(JobStatus.ASSIGNED_TO_TECHNICIAN);
          jobRepository.save(copy1);
          return null;
        });

    // Verify version in DB is now 1.
    Integer versionInDb =
        jdbc.queryForObject("SELECT version FROM jobs WHERE id = ?", Integer.class, jobId);
    assertThat(versionInDb).isEqualTo(1);

    // TX3: copy2 still carries version=0 — the second save must fail.
    assertThatThrownBy(
            () ->
                tx.execute(
                    s -> {
                      copy2.setStatus(JobStatus.IN_PROGRESS);
                      return jobRepository.save(copy2);
                    }))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private AppointmentSchema publishRequiredFieldSchema(UUID tenantId) {
    FieldDefinition requiredField =
        new FieldDefinition(
            REQUIRED_FIELD_KEY, "Vehicle Make", "text", true, 1, null, null, null, null);
    return schemaService.publishNewVersion(tenantId, SCHEMA_NAME, List.of(requiredField));
  }

  private Customer buildCustomer(UUID tenantId) {
    Customer c = new Customer();
    c.setTenantId(tenantId);
    c.setName("Test Customer");
    c.setPhone("+15033334444");
    c.setAddressLine("100 Test Ave");
    c.setCity("Hillsboro");
    c.setState("OR");
    c.setZip("97124");
    // location is required (NOT NULL) — use a fixed WGS-84 point for Hillsboro, OR.
    org.locationtech.jts.geom.GeometryFactory gf =
        new org.locationtech.jts.geom.GeometryFactory(
            new org.locationtech.jts.geom.PrecisionModel(), 4326);
    c.setLocation(gf.createPoint(new org.locationtech.jts.geom.Coordinate(-122.9898, 45.5229)));
    return c;
  }

  private Job buildJob(
      UUID tenantId, UUID customerId, UUID schemaId, Map<String, Object> customFields) {
    Job j = new Job();
    j.setTenantId(tenantId);
    j.setCustomerId(customerId);
    j.setAppointmentSchemaId(schemaId);
    j.setStatus(JobStatus.SUBMITTED);
    j.setCustomFields(customFields);
    return j;
  }
}
