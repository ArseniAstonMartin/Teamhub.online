package com.teamhub.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.teamhub.customer.dto.CreateCustomerRequest;
import com.teamhub.integration.geocoding.GeocodeResult;
import com.teamhub.integration.geocoding.GeocodingService;
import com.teamhub.shared.TenantContext;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link CustomerService} (TASK-126, customer location validation). */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class CustomerServiceUnitTest {

  @Mock private CustomerRepository customerRepository;

  @Mock private GeocodingService geocodingService;

  private CustomerService service() {
    return new CustomerService(customerRepository, geocodingService);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void restore_archivedCustomer_clearsArchivedAt() {
    UUID tenantId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    TenantContext.set(tenantId);

    Customer customer = new Customer();
    customer.setId(customerId);
    customer.setTenantId(tenantId);
    customer.setName("Archived Co");
    customer.setArchivedAt(Instant.now());

    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
    when(customerRepository.save(customer)).thenAnswer(inv -> inv.getArgument(0));

    Customer restored = service().restore(customerId);

    assertThat(restored.getArchivedAt()).isNull();
    verify(customerRepository).save(customer);
  }

  @Test
  void restore_missingCustomer_throwsNotFound() {
    UUID tenantId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    TenantContext.set(tenantId);

    when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service().restore(customerId))
        .isInstanceOf(CustomerNotFoundException.class);
  }

  @Test
  void restore_wrongTenant_throwsNotFound() {
    UUID tenantId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    TenantContext.set(tenantId);

    Customer customer = new Customer();
    customer.setId(customerId);
    customer.setTenantId(UUID.randomUUID());
    customer.setArchivedAt(Instant.now());

    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

    assertThatThrownBy(() -> service().restore(customerId))
        .isInstanceOf(CustomerNotFoundException.class);
  }

  @Test
  void createOrUpsert_withLatLon_persistsLocation() {
    UUID tenantId = UUID.randomUUID();
    TenantContext.set(tenantId);

    when(customerRepository.findByTenantIdAndPhoneAndArchivedAtIsNull(tenantId, "+15035551234"))
        .thenReturn(Optional.empty());
    when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

    CreateCustomerRequest req =
        new CreateCustomerRequest(
            "Alice",
            "+15035551234",
            null,
            null,
            "123 Main St",
            "Portland",
            "OR",
            "97201",
            45.5,
            -122.6,
            null);

    Customer saved = service().createOrUpsert(req);

    assertThat(saved.getLocation()).isNotNull();
    assertThat(saved.getLocation().getY()).isEqualTo(45.5);
    assertThat(saved.getLocation().getX()).isEqualTo(-122.6);
    verify(geocodingService, never()).geocode(any());
  }

  @Test
  void createOrUpsert_withAddress_geocodesAndFillsLocation() {
    UUID tenantId = UUID.randomUUID();
    TenantContext.set(tenantId);

    when(customerRepository.findByTenantIdAndPhoneAndArchivedAtIsNull(tenantId, "+15035559999"))
        .thenReturn(Optional.empty());
    when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
    when(geocodingService.geocode("123 Main St, Portland, OR"))
        .thenReturn(new GeocodeResult("Portland", "OR", "97201", 45.52, -122.68));

    CreateCustomerRequest req =
        new CreateCustomerRequest(
            "Bob",
            "+15035559999",
            null,
            null,
            "123 Main St",
            null,
            null,
            null,
            null,
            null,
            "123 Main St, Portland, OR");

    Customer saved = service().createOrUpsert(req);

    assertThat(saved.getCity()).isEqualTo("Portland");
    assertThat(saved.getState()).isEqualTo("OR");
    assertThat(saved.getZip()).isEqualTo("97201");
    assertThat(saved.getLocation().getY()).isEqualTo(45.52);
    assertThat(saved.getLocation().getX()).isEqualTo(-122.68);
  }

  @Test
  void createOrUpsert_withAddressLineOnly_geocodesAddressLine() {
    UUID tenantId = UUID.randomUUID();
    TenantContext.set(tenantId);

    when(customerRepository.findByTenantIdAndPhoneAndArchivedAtIsNull(tenantId, "+15036667777"))
        .thenReturn(Optional.empty());
    when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
    when(geocodingService.geocode("456 Oak Ave"))
        .thenReturn(new GeocodeResult("Salem", "OR", "97301", 44.94, -123.03));

    CreateCustomerRequest req =
        new CreateCustomerRequest(
            "Carol", "+15036667777", null, null, "456 Oak Ave", null, null, null, null, null, null);

    Customer saved = service().createOrUpsert(req);

    verify(geocodingService).geocode("456 Oak Ave");
    assertThat(saved.getCity()).isEqualTo("Salem");
    assertThat(saved.getLocation()).isNotNull();
  }

  @Test
  void createOrUpsert_withoutResolvableLocation_throwsCustomerAddressRequired() {
    UUID tenantId = UUID.randomUUID();
    TenantContext.set(tenantId);

    when(customerRepository.findByTenantIdAndPhoneAndArchivedAtIsNull(tenantId, "+15038880003"))
        .thenReturn(Optional.empty());

    CreateCustomerRequest req =
        new CreateCustomerRequest(
            "No Location",
            "+15038880003",
            null,
            null,
            "—",
            "Portland",
            "OR",
            "97201",
            null,
            null,
            null);

    assertThatThrownBy(() -> service().createOrUpsert(req))
        .isInstanceOf(CustomerAddressRequiredException.class)
        .satisfies(
            ex ->
                assertThat(((CustomerAddressRequiredException) ex).getCode())
                    .isEqualTo(CustomerAddressRequiredException.CODE));
    verify(customerRepository, never()).save(any());
  }

  @Test
  void createOrUpsert_geocodeBlankCityStateZip_keepsNormalizedDashValues() {
    UUID tenantId = UUID.randomUUID();
    TenantContext.set(tenantId);

    when(customerRepository.findByTenantIdAndPhoneAndArchivedAtIsNull(tenantId, "+15034445555"))
        .thenReturn(Optional.empty());
    when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
    when(geocodingService.geocode("100 Geocode Ln"))
        .thenReturn(new GeocodeResult("  ", "", "   ", 45.1, -122.1));

    CreateCustomerRequest req =
        new CreateCustomerRequest(
            "Geo Blanks",
            "+15034445555",
            null,
            null,
            "100 Geocode Ln",
            null,
            null,
            null,
            null,
            null,
            "100 Geocode Ln");

    Customer saved = service().createOrUpsert(req);

    assertThat(saved.getCity()).isEqualTo("—");
    assertThat(saved.getState()).isEqualTo("—");
    assertThat(saved.getZip()).isEqualTo("—");
    assertThat(saved.getLocation().getY()).isEqualTo(45.1);
  }

  @Test
  void createOrUpsert_blankCityStateZip_normalizedToDash() {
    UUID tenantId = UUID.randomUUID();
    TenantContext.set(tenantId);

    when(customerRepository.findByTenantIdAndPhoneAndArchivedAtIsNull(tenantId, "+15037778888"))
        .thenReturn(Optional.empty());
    when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

    CreateCustomerRequest req =
        new CreateCustomerRequest(
            "Dash Fields",
            "+15037778888",
            null,
            null,
            "789 Pine Rd",
            "  ",
            null,
            "",
            45.0,
            -122.0,
            null);

    Customer saved = service().createOrUpsert(req);

    assertThat(saved.getCity()).isEqualTo("—");
    assertThat(saved.getState()).isEqualTo("—");
    assertThat(saved.getZip()).isEqualTo("—");
  }

  @Test
  void restore_activeCustomer_throwsNotFound() {
    UUID tenantId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    TenantContext.set(tenantId);

    Customer customer = new Customer();
    customer.setId(customerId);
    customer.setTenantId(tenantId);

    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

    assertThatThrownBy(() -> service().restore(customerId))
        .isInstanceOf(CustomerNotFoundException.class);
  }
}
