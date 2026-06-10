package com.teamhub.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

/** Unit tests for {@link CustomerService#restore} (TASK-126). */
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
