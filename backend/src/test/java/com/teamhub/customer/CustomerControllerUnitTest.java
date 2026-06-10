package com.teamhub.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.teamhub.customer.dto.CustomerResponse;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link CustomerController#restore} (TASK-126). */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class CustomerControllerUnitTest {

  @Mock private CustomerService customerService;

  private CustomerController controller() {
    return new CustomerController(customerService);
  }

  @Test
  void restore_mapsRestoredCustomerToResponse() {
    UUID customerId = UUID.randomUUID();
    Customer customer = new Customer();
    customer.setId(customerId);
    customer.setTenantId(UUID.randomUUID());
    customer.setName("Restored Co");
    customer.setPhone("+15033334444");
    when(customerService.restore(customerId)).thenReturn(customer);

    CustomerResponse response = controller().restore(customerId);

    assertThat(response.id()).isEqualTo(customerId);
    assertThat(response.name()).isEqualTo("Restored Co");
    assertThat(response.phone()).isEqualTo("+15033334444");
  }
}
