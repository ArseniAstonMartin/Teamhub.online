package com.teamhub.customer.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.teamhub.customer.Customer;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

/** Unit tests for {@link CustomerResponse} DTO and its phone-masking logic (PRD §11.2). */
@Tag("unit")
class CustomerResponseTest {

  private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

  // ---------------------------------------------------------------------------
  // maskPhone edge cases
  // ---------------------------------------------------------------------------

  @Test
  void maskPhone_nullPhone_returnsNull() {
    assertThat(CustomerResponse.maskPhone(null)).isNull();
  }

  @Test
  void maskPhone_phoneShorterThanNineChars_returnsUnchanged() {
    String short8 = "+1503123";
    assertThat(CustomerResponse.maskPhone(short8)).isEqualTo(short8);
  }

  @Test
  void maskPhone_exactlyNineChars_returnsMasked() {
    // keepStart = max(9-7, 4) = 4 → "+150" + "***" + "2345"
    assertThat(CustomerResponse.maskPhone("+150312345")).isEqualTo("+150***2345");
  }

  @Test
  void maskPhone_fullE164Phone_returnsMasked() {
    assertThat(CustomerResponse.maskPhone("+15033334444")).isEqualTo("+1503***4444");
  }

  // ---------------------------------------------------------------------------
  // from() — null location branch
  // ---------------------------------------------------------------------------

  @Test
  void from_customerWithNullLocation_hasNullLatAndLon() {
    Customer c = buildMinimalCustomer("+15031234567");
    c.setLocation(null);

    CustomerResponse resp = CustomerResponse.from(c, false);

    assertThat(resp.lat()).isNull();
    assertThat(resp.lon()).isNull();
  }

  @Test
  void from_customerWithLocation_hasLatAndLon() {
    Customer c = buildMinimalCustomer("+15031234567");
    c.setLocation(GF.createPoint(new Coordinate(-122.9898, 45.5229)));

    CustomerResponse resp = CustomerResponse.from(c, false);

    assertThat(resp.lon()).isEqualTo(-122.9898);
    assertThat(resp.lat()).isEqualTo(45.5229);
  }

  // ---------------------------------------------------------------------------
  // from() — maskPhone=true path
  // ---------------------------------------------------------------------------

  @Test
  void from_withMaskPhoneTrue_masksPhone() {
    Customer c = buildMinimalCustomer("+15033334444");
    c.setLocation(GF.createPoint(new Coordinate(-122.9898, 45.5229)));

    CustomerResponse resp = CustomerResponse.from(c, true);

    assertThat(resp.phone()).isEqualTo("+1503***4444");
  }

  // ---------------------------------------------------------------------------
  // Helper
  // ---------------------------------------------------------------------------

  private Customer buildMinimalCustomer(String phone) {
    Customer c = new Customer();
    c.setTenantId(UUID.randomUUID());
    c.setName("Test");
    c.setPhone(phone);
    c.setAddressLine("1 Main St");
    c.setCity("Portland");
    c.setState("OR");
    c.setZip("97201");
    return c;
  }
}
