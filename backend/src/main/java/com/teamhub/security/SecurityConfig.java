package com.teamhub.security;

import com.teamhub.security.rbac.PermissionJwtAuthenticationConverter;
import com.teamhub.shared.TenantContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Stateless JWT security (PRD §6.2, §7.2) with permission-based method security (PRD §5.4, §5.5).
 *
 * <p>Public endpoints (login/refresh/logout, health/info, OpenAPI docs) are open; every other
 * request requires a valid RS256 access token validated by the resource server. The token's {@code
 * role} claim is translated into per-permission authorities by {@link
 * PermissionJwtAuthenticationConverter}, so {@code @PreAuthorize} guards reference permission codes
 * (e.g. {@code hasAuthority('PERM_job.delete.hard')}), never hard-coded roles.
 *
 * <p>After validation, {@link TenantContextFilter} reads the {@code tenant_id} claim and binds it
 * to the request thread so PostgreSQL Row-Level Security is scoped to the caller's tenant.
 *
 * <p>{@code @EnableMethodSecurity} activates {@code @PreAuthorize} processing. Authorization
 * denials are rendered as 403 with the uniform error envelope (PRD §23.4) by {@link
 * RestAccessDeniedHandler}. Unauthenticated access is rendered as 401 with the same envelope by
 * {@link RestAuthenticationEntryPoint}. CORS allows only the single SPA origin configured via
 * {@link CorsConfig} / {@code CORS_ALLOWED_ORIGIN}.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      PermissionJwtAuthenticationConverter jwtAuthenticationConverter,
      AccessDeniedHandler accessDeniedHandler,
      AuthenticationEntryPoint authenticationEntryPoint,
      CorsConfigurationSource corsConfigurationSource)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/v1/auth/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info")
                    .permitAll()
                    .requestMatchers("/internal/sentry/**", "/api/v1/internal/sentry/**")
                    .permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                    .authenticationEntryPoint(authenticationEntryPoint))
        .exceptionHandling(
            handling ->
                handling
                    .accessDeniedHandler(accessDeniedHandler)
                    .authenticationEntryPoint(authenticationEntryPoint))
        .addFilterAfter(new TenantContextFilter(), BearerTokenAuthenticationFilter.class);

    return http.build();
  }
}
