package com.teamhub.customer;

import com.teamhub.customer.dto.CreateCustomerRequest;
import com.teamhub.customer.dto.CustomerResponse;
import com.teamhub.customer.dto.UpdateCustomerRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer CRUD endpoints (PRD §11.2, §11.3, TASK-037).
 *
 * <p>All read operations require {@code PERM_customer.read.tenant} which is held by DISPATCHER,
 * ADMIN, and TECHNICIAN (V6 migration). Write operations (POST, PATCH, DELETE) require {@code
 * PERM_job.create} (DISPATCHER + ADMIN) because customer records are created as part of job
 * creation workflows (PRD §11.3).
 *
 * <p>Phone masking: TECHNICIAN callers see only the masked form of each phone number (e.g. {@code
 * +1503***4444}) because no jobs table exists yet to determine job-level linkage. See {@link
 * CustomerResponse#maskPhone} for the masking algorithm and the TODO for the future per-customer
 * jobs-link check (PRD §11.2).
 */
@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "customers", description = "Customer CRM endpoints — PRD §11.2, §11.3")
public class CustomerController {

  private static final String ROLE_TECHNICIAN = "TECHNICIAN";

  private final CustomerService customerService;

  public CustomerController(CustomerService customerService) {
    this.customerService = customerService;
  }

  /**
   * Lists customers with optional free-text search and archived-record inclusion.
   *
   * @param q optional search string matched case-insensitively against name, phone, and email
   * @param includeArchived when {@code true}, soft-archived customers are included (default false)
   * @param pageable page, size, and sort parameters (default page size 20)
   */
  @GetMapping
  @PreAuthorize("hasAuthority('PERM_customer.read.tenant')")
  @Operation(
      summary = "List customers",
      description =
          "Paginated list with optional free-text search (q) and archived-record inclusion.")
  public Page<CustomerResponse> list(
      @RequestParam(required = false) String q,
      @RequestParam(name = "include_archived", defaultValue = "false") boolean includeArchived,
      @PageableDefault(size = 20) Pageable pageable,
      Authentication authentication) {
    boolean maskPhone = isTechnician(authentication);
    return customerService
        .list(q, includeArchived, pageable)
        .map(c -> CustomerResponse.from(c, maskPhone));
  }

  /** Returns a single customer by ID. */
  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_customer.read.tenant')")
  @Operation(summary = "Get a customer by ID")
  public CustomerResponse get(@PathVariable UUID id, Authentication authentication) {
    boolean maskPhone = isTechnician(authentication);
    return CustomerResponse.from(customerService.getById(id), maskPhone);
  }

  /**
   * Creates a new customer or upserts an existing one when a customer with the same phone already
   * exists in the tenant (PRD §11.3). On upsert the existing customer's {@code id} is returned and
   * non-null fields from the request are merged.
   */
  @PostMapping
  @PreAuthorize("hasAuthority('PERM_job.create')")
  @Operation(
      summary = "Create or upsert a customer",
      description =
          "PRD §11.3: if a customer with the same phone already exists in the tenant the existing"
              + " record is returned and non-null fields are merged (upsert-on-phone semantics).")
  public ResponseEntity<CustomerResponse> create(
      @Valid @RequestBody CreateCustomerRequest request) {
    Customer customer = customerService.createOrUpsert(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(CustomerResponse.from(customer, false));
  }

  /** Partially updates an existing customer; null fields are left unchanged. */
  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_job.create')")
  @Operation(
      summary = "Partially update a customer",
      description = "Only non-null fields are written.")
  public CustomerResponse update(
      @PathVariable UUID id, @RequestBody UpdateCustomerRequest request) {
    return CustomerResponse.from(customerService.update(id, request), false);
  }

  /**
   * Soft-archives a customer (sets {@code archived_at}). Archived customers are excluded from
   * default list queries but preserved for history (PRD §11.3).
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_job.create')")
  @Operation(
      summary = "Soft-archive a customer",
      description = "Sets archived_at; excluded from default list unless include_archived=true.")
  public ResponseEntity<Void> archive(@PathVariable UUID id) {
    customerService.archive(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Returns {@code true} when the authenticated caller holds the TECHNICIAN role.
   *
   * <p>TODO (PRD §11.2): Once the {@code jobs} table exists, pass the customer ID here and check
   * whether the customer is linked to a job claimed by this technician. Return {@code false} (no
   * masking) when linked; {@code true} (mask) when not linked. For now ALL customers are treated as
   * unlinked for TECHNICIAN callers — full phone masking applied.
   */
  private boolean isTechnician(Authentication authentication) {
    if (!(authentication instanceof JwtAuthenticationToken jwtToken)) {
      return false;
    }
    return ROLE_TECHNICIAN.equals(jwtToken.getToken().getClaimAsString("role"));
  }
}
