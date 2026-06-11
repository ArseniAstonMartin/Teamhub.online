package com.teamhub.internal;

import com.teamhub.security.RestAccessDeniedHandler;
import com.teamhub.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security filter chain for {@code /api/v1/internal/**} (PRD §6.4, TASK-131).
 *
 * <p>Evaluated before the tenant JWT chain. IP-filtered; login is permit-all; all other routes
 * require a {@code TEAMHUB_STAFF} bearer token.
 */
@Configuration
public class InternalSecurityConfig {

  @SuppressWarnings("java:S4502")
  @Bean
  @Order(2)
  public SecurityFilterChain internalFilterChain(
      HttpSecurity http,
      InternalProperties internalProperties,
      StaffJwtAuthenticationConverter staffJwtAuthenticationConverter,
      RestAuthenticationEntryPoint authenticationEntryPoint,
      RestAccessDeniedHandler accessDeniedHandler)
      throws Exception {

    InternalIpAllowListFilter ipFilter = new InternalIpAllowListFilter(internalProperties);
    InternalStaffRoleFilter staffRoleFilter = new InternalStaffRoleFilter();

    http.securityMatcher(
            request ->
                request.getRequestURI().startsWith("/api/v1/internal/")
                    && !request.getRequestURI().startsWith("/api/v1/internal/sentry/"))
        .csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.POST, "/api/v1/internal/auth/login")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(staffJwtAuthenticationConverter))
                    .authenticationEntryPoint(authenticationEntryPoint))
        .exceptionHandling(
            handling ->
                handling
                    .accessDeniedHandler(accessDeniedHandler)
                    .authenticationEntryPoint(authenticationEntryPoint))
        .addFilterBefore(ipFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(staffRoleFilter, BearerTokenAuthenticationFilter.class);

    return http.build();
  }
}
