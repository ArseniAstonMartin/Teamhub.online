package com.teamhub.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.teamhub.config.IntegrationTest;
import com.teamhub.tenancy.InternalTenantService;
import com.teamhub.tenancy.Tenant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the {@code customers} table migration, JPA entity, and RLS policy (PRD
 * §11.1, TASK-036).
 *
 * <p>Tests run against a real PostgreSQL + PostGIS container via Testcontainers. The {@code
 * postgres} superuser used by Testcontainers has {@code BYPASSRLS}, so JPA inserts go through
 * unfiltered. RLS is exercised by switching to the {@code teamhub_app} role inside the
 * Spring-managed transaction (same pattern as {@code RowLevelSecurityIT}).
 */
@SpringBootTest
@IntegrationTest
@Transactional
class CustomerIT {

  /** Hillsboro, OR — reference location used across test steps. */
  private static final double HILLSBORO_LON = -122.9898;

  private static final double HILLSBORO_LAT = 45.5229;

  /** WGS-84 SRID used for all geography(Point,4326) columns. */
  private static final int SRID_WGS84 = 4326;

  private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), SRID_WGS84);

  @Autowired private CustomerRepository customerRepository;

  @Autowired private InternalTenantService tenantService;

  @Autowired private JdbcTemplate jdbcTemplate;

  @PersistenceContext private EntityManager em;

  // ---------------------------------------------------------------------------
  // Step 1 — Insert a customer; ST_AsText(location) returns the expected POINT
  // ---------------------------------------------------------------------------

  @Test
  void insertCustomer_locationStoredAndReturnedAsPoint() {
    Tenant tenant = tenantService.provision("Hillsboro Plumbing", "admin@hillsboro.com");

    Customer customer = buildCustomer(tenant.getId(), HILLSBORO_LON, HILLSBORO_LAT);
    customerRepository.save(customer);
    em.flush();

    UUID id = customer.getId();

    Double lon =
        jdbcTemplate.queryForObject(
            "SELECT ST_X(location::geometry) FROM customers WHERE id = ?", Double.class, id);
    Double lat =
        jdbcTemplate.queryForObject(
            "SELECT ST_Y(location::geometry) FROM customers WHERE id = ?", Double.class, id);

    assertThat(lon).isNotNull().isCloseTo(HILLSBORO_LON, within(0.0001));
    assertThat(lat).isNotNull().isCloseTo(HILLSBORO_LAT, within(0.0001));

    String wkt =
        jdbcTemplate.queryForObject(
            "SELECT ST_AsText(location) FROM customers WHERE id = ?", String.class, id);
    assertThat(wkt).isNotNull().startsWith("POINT(");
  }

  // ---------------------------------------------------------------------------
  // Step 2 — ST_DWithin range query works and uses the GIST index
  // ---------------------------------------------------------------------------

  @Test
  void spatialQuery_stDWithinFindsNearbyCustomer_andGistIndexIsUsed() {
    Tenant tenant = tenantService.provision("Beaverton HVAC", "admin@beaverton.com");

    // Insert a customer 500 m from Hillsboro reference point (same coords for simplicity).
    Customer nearby = buildCustomer(tenant.getId(), HILLSBORO_LON, HILLSBORO_LAT);
    // Insert a customer far away (Portland, ~20 km).
    Customer farAway = buildCustomer(tenant.getId(), -122.6784, 45.5051);
    customerRepository.saveAll(List.of(nearby, farAway));
    em.flush();

    // ST_DWithin on geography measures geodesic distance in metres.
    String refWkt = "SRID=4326;POINT(" + HILLSBORO_LON + " " + HILLSBORO_LAT + ")";
    double radiusMetres = 1_000.0; // 1 km

    List<Map<String, Object>> results =
        jdbcTemplate.queryForList(
            "SELECT id FROM customers" + " WHERE ST_DWithin(location, ST_GeographyFromText(?), ?)",
            refWkt,
            radiusMetres);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).get("id").toString()).isEqualTo(nearby.getId().toString());

    // Verify the GIST index is referenced in the query plan.
    // Disable sequential scans so the planner is forced to use the index.
    jdbcTemplate.execute("SET LOCAL enable_seqscan = off");
    List<String> planLines =
        jdbcTemplate.query(
            "EXPLAIN SELECT id FROM customers"
                + " WHERE ST_DWithin(location, ST_GeographyFromText(?), ?)",
            (rs, rowNum) -> rs.getString(1),
            refWkt,
            radiusMetres);
    String planText = String.join("\n", planLines);
    assertThat(planText).containsIgnoringCase("customers_location_gist");
  }

  // ---------------------------------------------------------------------------
  // Step 3 — Cross-tenant SELECT returns zero rows (RLS check)
  // ---------------------------------------------------------------------------

  @Test
  void crossTenantSelect_returnsZeroRows() {
    Tenant tenantA = tenantService.provision("Alpha Plumbing", "admin@alpha.com");
    em.flush();

    // Insert a customer for tenant A directly via JDBC (postgres has BYPASSRLS).
    jdbcTemplate.update(
        "INSERT INTO customers"
            + " (id, tenant_id, name, phone, address_line, city, state, zip, location)"
            + " VALUES (gen_random_uuid(), ?, 'Jane Smith', '+15031112222',"
            + "         '1 Main St', 'Hillsboro', 'OR', '97124',"
            + "         ST_GeographyFromText('SRID=4326;POINT(-122.9898 45.5229)'))",
        tenantA.getId());

    // Switch to the RLS-enforced application role.
    jdbcTemplate.execute("SET LOCAL ROLE teamhub_app");

    // No tenant GUC → NULLIF(current_setting('app.current_tenant_id', true), '') = NULL
    // tenant_id = NULL is always FALSE → deny-by-default.
    List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT id FROM customers");
    assertThat(rows).isEmpty();

    // Even with a different tenant's GUC, tenant A's row must not be visible.
    UUID otherTenantId = UUID.randomUUID();
    jdbcTemplate.execute("SET LOCAL app.current_tenant_id = '" + otherTenantId + "'");
    List<Map<String, Object>> rowsOtherTenant =
        jdbcTemplate.queryForList("SELECT id FROM customers");
    assertThat(rowsOtherTenant).isEmpty();
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private Customer buildCustomer(UUID tenantId, double lon, double lat) {
    Point point = GF.createPoint(new Coordinate(lon, lat));
    Customer c = new Customer();
    c.setTenantId(tenantId);
    c.setName("Test Customer");
    c.setPhone("+15033334444");
    c.setAddressLine("123 Main St");
    c.setCity("Hillsboro");
    c.setState("OR");
    c.setZip("97124");
    c.setLocation(point);
    return c;
  }
}
