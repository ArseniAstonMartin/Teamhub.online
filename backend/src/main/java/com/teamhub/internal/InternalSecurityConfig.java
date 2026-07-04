package com.teamhub.internal;

import com.teamhub.security.RestAccessDeniedHandler;
import com.teamhub.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Security filter chain for {@code /api/v1/internal/**} (PRD §6.4, TASK-131).
 *
 * <p>Evaluated before the tenant JWT chain. IP-filtered; login is permit-all; all other routes
 * require a {@code TEAMHUB_STAFF} bearer token.
 */
@Configuration
public class InternalSecurityConfig {

  private static final String CONTENT_SECURITY_POLICY =
      "default-src 'self'; "
          + "script-src 'self' 'unsafe-inline'; "
          + "style-src 'self' 'unsafe-inline'; "
          + "img-src 'self' data:; "
          + "font-src 'self' data:; "
          + "connect-src 'self'; "
          + "object-src 'none'; "
          + "base-uri 'self'; "
          + "form-action 'self'; "
          + "frame-ancestors 'none'";

  private static void applySecurityHeaders(HeadersConfigurer<HttpSecurity> headers) {
    headers
        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
        .contentTypeOptions(contentTypeOptions -> { })
        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER_WHEN_DOWNGRADE))
        .frameOptions(frame -> frame.deny());
  }

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
        .headers(InternalSecurityConfig::applySecurityHeaders)
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
