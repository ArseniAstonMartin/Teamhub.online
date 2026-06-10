package com.teamhub;

import static org.assertj.core.api.Assertions.assertThat;

import com.teamhub.config.IntegrationTest;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@IntegrationTest
class FlywayMigrationTest {

  @Autowired private JdbcTemplate jdbc;

  @Autowired private Flyway flyway;

  @Test
  void v1MigrationCreatesTenantsTable() {
    List<String> tables =
        jdbc.queryForList(
            "SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class);
    assertThat(tables).contains("tenants", "users");
  }

  @Test
  void v1MigrationAppearsInFlywayHistory() {
    boolean v1Applied =
        jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = true", String.class)
            .contains("1");
    assertThat(v1Applied).isTrue();
  }

  @Test
  void flywayReportsNoOutstandingMigrations() {
    assertThat(flyway.info().pending()).isEmpty();
  }
}
