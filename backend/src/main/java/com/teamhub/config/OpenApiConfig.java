package com.teamhub.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 spec configuration (PRD §23.5, TASK-026).
 *
 * <p>Registers the JWT Bearer auth scheme globally so every endpoint shows the lock icon in Swagger
 * UI. Tags group endpoints by module (auth, jobs, customers, schemas, billing, eventing).
 */
@Configuration
public class OpenApiConfig {

  private static final String BEARER_AUTH = "BearerAuth";

  @Bean
  public OpenAPI customOpenAPI(
      @Value("${spring.application.version:dev}") String applicationVersion) {
    return new OpenAPI()
        .info(new Info().title("Teamhub API").version(applicationVersion))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER_AUTH,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .name(BEARER_AUTH)))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
        .tags(
            List.of(
                new Tag().name("auth").description("Authentication — login, refresh, logout"),
                new Tag().name("jobs").description("Job CRUD and lifecycle — PRD §9, §10"),
                new Tag()
                    .name("customers")
                    .description("Customer CRM endpoints — PRD §11.2, §11.3"),
                new Tag()
                    .name("schemas")
                    .description("Appointment-schema versioning — PRD §5.3, §8.2"),
                new Tag()
                    .name("billing")
                    .description("Billing / Stripe integration (placeholder) — PRD §16"),
                new Tag()
                    .name("eventing")
                    .description("Eventing / outbox stream (placeholder) — PRD §20")));
  }
}
