package com.teamhub.customer;

import com.teamhub.customer.dto.CreateCustomerRequest;
import com.teamhub.customer.dto.UpdateCustomerRequest;
import com.teamhub.integration.geocoding.GeocodeResult;
import com.teamhub.integration.geocoding.GeocodingService;
import com.teamhub.shared.TenantContext;
import java.time.Instant;
import java.util.UUID;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Business logic for customer CRUD operations (PRD §11.3, TASK-037). */
@Service
@Transactional
public class CustomerService {

  private static final int SRID_WGS84 = 4326;

  private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), SRID_WGS84);

  private final CustomerRepository customerRepository;

  private final GeocodingService geocodingService;

  public CustomerService(CustomerRepository customerRepository, GeocodingService geocodingService) {
    this.customerRepository = customerRepository;
    this.geocodingService = geocodingService;
  }

  /**
   * Returns a paginated list of customers for the current tenant.
   *
   * @param q optional free-text search against name, phone, and email; {@code null} or empty
   *     returns all
   * @param includeArchived when {@code true}, archived (soft-deleted) customers are included
   * @param pageable pagination and sort parameters
   */
  @Transactional(readOnly = true)
  public Page<Customer> list(String q, boolean includeArchived, Pageable pageable) {
    UUID tenantId = TenantContext.get();
    return customerRepository.search(tenantId, q, includeArchived, pageable);
  }

  /**
   * Returns a single customer by id for the current tenant.
   *
   * @throws CustomerNotFoundException when the customer does not exist within this tenant
   */
  @Transactional(readOnly = true)
  public Customer getById(UUID id) {
    UUID tenantId = TenantContext.get();
    return customerRepository
        .findById(id)
        .filter(c -> c.getTenantId().equals(tenantId))
        .orElseThrow(() -> new CustomerNotFoundException(id));
  }

  /**
   * Creates a new customer or upserts an existing one when a customer with the same phone already
   * exists in the tenant (PRD §11.3).
   *
   * <p>On upsert, all non-null fields from the request are merged into the existing record. The
   * existing customer's {@code id} is preserved and returned so the caller can detect the upsert by
   * comparing ids across two consecutive calls.
   *
   * @param req validated request body
   * @return the persisted customer (new or updated)
   */
  public Customer createOrUpsert(CreateCustomerRequest req) {
    UUID tenantId = TenantContext.get();
    return customerRepository
        .findByTenantIdAndPhoneAndArchivedAtIsNull(tenantId, req.phone())
        .map(existing -> mergeInto(existing, req))
        .orElseGet(() -> customerRepository.save(toEntity(tenantId, req)));
  }

  /**
   * Partially updates an existing customer. Only non-null fields in the request overwrite the
   * stored values; null fields are left unchanged.
   *
   * @throws CustomerNotFoundException when the customer does not exist within this tenant
   */
  public Customer update(UUID id, UpdateCustomerRequest req) {
    UUID tenantId = TenantContext.get();
    Customer customer =
        customerRepository
            .findById(id)
            .filter(c -> c.getTenantId().equals(tenantId))
            .orElseThrow(() -> new CustomerNotFoundException(id));

    if (req.name() != null) {
      customer.setName(req.name());
    }
    if (req.email() != null) {
      customer.setEmail(req.email());
    }
    if (req.companyName() != null) {
      customer.setCompanyName(req.companyName());
    }
    if (req.addressLine() != null) {
      customer.setAddressLine(req.addressLine());
    }
    if (req.city() != null) {
      customer.setCity(req.city());
    }
    if (req.state() != null) {
      customer.setState(req.state());
    }
    if (req.zip() != null) {
      customer.setZip(req.zip());
    }
    if (req.lat() != null && req.lon() != null) {
      customer.setLocation(buildPoint(req.lon(), req.lat()));
    }
    return customerRepository.save(customer);
  }

  /**
   * Soft-archives a customer by setting {@code archived_at} to the current instant (PRD §11.3). The
   * customer is excluded from default list queries but preserved in history.
   *
   * @throws CustomerNotFoundException when the customer does not exist within this tenant
   */
  public void archive(UUID id) {
    UUID tenantId = TenantContext.get();
    Customer customer =
        customerRepository
            .findById(id)
            .filter(c -> c.getTenantId().equals(tenantId))
            .orElseThrow(() -> new CustomerNotFoundException(id));
    customer.setArchivedAt(Instant.now());
    customerRepository.save(customer);
  }

  /**
   * Restores a soft-archived customer by clearing {@code archived_at} (PRD §11.3, TASK-126).
   *
   * @throws CustomerNotFoundException when the customer does not exist within this tenant or is not
   *     archived
   */
  public Customer restore(UUID id) {
    UUID tenantId = TenantContext.get();
    Customer customer =
        customerRepository
            .findById(id)
            .filter(c -> c.getTenantId().equals(tenantId))
            .filter(c -> c.getArchivedAt() != null)
            .orElseThrow(() -> new CustomerNotFoundException(id));
    customer.setArchivedAt(null);
    return customerRepository.save(customer);
  }

  private Customer mergeInto(Customer existing, CreateCustomerRequest req) {
    if (req.name() != null && !req.name().isBlank()) {
      existing.setName(req.name());
    }
    if (req.email() != null) {
      existing.setEmail(req.email());
    }
    if (req.companyName() != null) {
      existing.setCompanyName(req.companyName());
    }
    if (req.addressLine() != null && !req.addressLine().isBlank()) {
      existing.setAddressLine(req.addressLine());
    }
    if (req.lat() != null && req.lon() != null) {
      if (req.city() != null && !req.city().isBlank()) {
        existing.setCity(req.city());
      }
      if (req.state() != null && !req.state().isBlank()) {
        existing.setState(req.state());
      }
      if (req.zip() != null && !req.zip().isBlank()) {
        existing.setZip(req.zip());
      }
      existing.setLocation(buildPoint(req.lon(), req.lat()));
    } else if (req.address() != null && !req.address().isBlank()) {
      GeocodeResult geo = geocodingService.geocode(req.address());
      if (geo.city() != null) {
        existing.setCity(geo.city());
      }
      if (geo.state() != null) {
        existing.setState(geo.state());
      }
      if (geo.zip() != null) {
        existing.setZip(geo.zip());
      }
      existing.setLocation(buildPoint(geo.lon(), geo.lat()));
    } else {
      if (req.city() != null && !req.city().isBlank()) {
        existing.setCity(req.city());
      }
      if (req.state() != null && !req.state().isBlank()) {
        existing.setState(req.state());
      }
      if (req.zip() != null && !req.zip().isBlank()) {
        existing.setZip(req.zip());
      }
    }
    return customerRepository.save(existing);
  }

  private Customer toEntity(UUID tenantId, CreateCustomerRequest req) {
    Customer c = new Customer();
    c.setTenantId(tenantId);
    c.setName(req.name());
    c.setPhone(req.phone());
    c.setEmail(req.email());
    c.setCompanyName(req.companyName());
    c.setAddressLine(req.addressLine());
    c.setCity(nonBlankOrDash(req.city()));
    c.setState(nonBlankOrDash(req.state()));
    c.setZip(nonBlankOrDash(req.zip()));
    applyLocation(c, req);
    requireLocation(c);
    return c;
  }

  private void applyLocation(Customer customer, CreateCustomerRequest req) {
    if (req.lat() != null && req.lon() != null) {
      customer.setLocation(buildPoint(req.lon(), req.lat()));
      return;
    }
    String geocodeInput = geocodeInput(req);
    if (geocodeInput == null) {
      return;
    }
    GeocodeResult geo = geocodingService.geocode(geocodeInput);
    if (geo.city() != null && !geo.city().isBlank()) {
      customer.setCity(geo.city());
    }
    if (geo.state() != null && !geo.state().isBlank()) {
      customer.setState(geo.state());
    }
    if (geo.zip() != null && !geo.zip().isBlank()) {
      customer.setZip(geo.zip());
    }
    customer.setLocation(buildPoint(geo.lon(), geo.lat()));
  }

  private static String geocodeInput(CreateCustomerRequest req) {
    if (req.address() != null && !req.address().isBlank()) {
      return req.address();
    }
    if (req.addressLine() != null
        && !req.addressLine().isBlank()
        && !"—".equals(req.addressLine())) {
      return req.addressLine();
    }
    return null;
  }

  private static String nonBlankOrDash(String value) {
    return value == null || value.isBlank() ? "—" : value;
  }

  private static void requireLocation(Customer customer) {
    if (customer.getLocation() == null) {
      throw new CustomerAddressRequiredException();
    }
  }

  private static Point buildPoint(double lon, double lat) {
    return GF.createPoint(new Coordinate(lon, lat));
  }
}
