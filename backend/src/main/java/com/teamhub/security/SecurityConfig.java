package com.teamhub.security;

import com.teamhub.auth.GoogleOAuth2FailureHandler;
import com.teamhub.auth.GoogleOAuth2SuccessHandler;
import com.teamhub.logging.MdcLoggingFilter;
import com.teamhub.ratelimit.RateLimitFilter;
import com.teamhub.security.rbac.PermissionJwtAuthenticationConverter;
import com.teamhub.shared.TenantContextFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
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
 *
 * <p>A dedicated {@code @Order(1)} filter chain handles {@code /actuator/**} using HTTP Basic auth
 * (PRD §21.5, TASK-014). {@code /actuator/health} and {@code /actuator/info} are public; all other
 * actuator endpoints require the {@code ACTUATOR_INTERNAL} role, whose credentials are supplied via
 * {@code ACTUATOR_USERNAME} / {@code ACTUATOR_PASSWORD} environment variables.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
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

  private Customizer<HeadersConfigurer<HttpSecurity>> securityHeadersCustomizer() {
    return headers ->
        headers
            .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
            .httpStrictTransportSecurity(
                hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
            .contentTypeOptions(Customizer.withDefaults())
            .referrerPolicy(
                referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER_WHEN_DOWNGRADE))
            .frameOptions(frame -> frame.deny());
  }

  /**
   * OAuth2 login filter chain — handles {@code /oauth2/**} and {@code /login/oauth2/**}.
   *
   * <p>A short-lived server-side session is required to validate the {@code state} anti-forgery
   * parameter that Spring Security generates when redirecting to Google. This chain is isolated
   * ({@code @Order(0)}, dedicated {@code securityMatcher}) so the rest of the application remains
   * fully stateless. CSRF remains enabled for this session-backed auth flow, while OAuth2 {@code
   * state} is still validated by Spring Security as an extra anti-forgery layer.
   *
   * <p>On success the {@link GoogleOAuth2SuccessHandler} resolves the Teamhub identity and issues
   * JWTs; on failure the {@link GoogleOAuth2FailureHandler} redirects to the SPA error page.
   */
  @SuppressWarnings("java:S4502")
  @Bean
  @Order(0)
  public SecurityFilterChain oauth2LoginFilterChain(
      HttpSecurity http,
      GoogleOAuth2SuccessHandler successHandler,
      GoogleOAuth2FailureHandler failureHandler)
      throws Exception {

    http.securityMatcher("/oauth2/**", "/login/oauth2/**")
        .csrf(Customizer.withDefaults())
        .headers(securityHeadersCustomizer())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .oauth2Login(
            oauth2 -> oauth2.successHandler(successHandler).failureHandler(failureHandler));

    return http.build();
  }

  /**
   * Actuator-only filter chain (HTTP Basic, no sessions). Evaluated before the JWT chain.
   *
   * <p>{@code /actuator/health} and {@code /actuator/info} are permit-all so liveness/readiness
   * probes work without credentials. All other {@code /actuator/**} paths require the {@code
   * ROLE_ACTUATOR_INTERNAL} authority.
   */
  // CSRF is safe to disable: this chain is stateless (no session cookies) and authenticates
  // via the Authorization header (HTTP Basic). CSRF attacks require cookie-based state.
  @SuppressWarnings("java:S4502")
  @Bean
  @Order(1)
  public SecurityFilterChain actuatorFilterChain(
      HttpSecurity http,
      @Value("${ACTUATOR_USERNAME:actuator}") String actuatorUsername,
      @Value("${ACTUATOR_PASSWORD:actuator-secret}") String actuatorPassword)
      throws Exception {

    InMemoryUserDetailsManager actuatorUserDetails =
        new InMemoryUserDetailsManager(
            User.withUsername(actuatorUsername)
                .password("{noop}" + actuatorPassword)
                .roles("ACTUATOR_INTERNAL")
                .build());

    // Use DelegatingPasswordEncoder so {noop} prefix is recognized even when the
    // application-wide PasswordEncoder bean is BCrypt (PasswordConfig).
    DaoAuthenticationProvider actuatorAuthProvider = new DaoAuthenticationProvider();
    actuatorAuthProvider.setUserDetailsService(actuatorUserDetails);
    actuatorAuthProvider.setPasswordEncoder(
        PasswordEncoderFactories.createDelegatingPasswordEncoder());

    http.securityMatcher("/actuator/**")
        .csrf(AbstractHttpConfigurer::disable)
        .headers(securityHeadersCustomizer())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authenticationProvider(actuatorAuthProvider)
        .httpBasic(Customizer.withDefaults())
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info")
                    .permitAll()
                    .anyRequest()
                    .hasRole("ACTUATOR_INTERNAL"));

    return http.build();
  }

  /**
   * JWT resource-server chain for all API traffic (evaluated after the actuator chain).
   *
   * <p>CSRF is disabled intentionally: the chain is fully stateless (no session cookies) and
   * authenticates exclusively via the {@code Authorization: Bearer <token>} header. CSRF attacks
   * require browser-managed cookies — there are none here.
   */
  @SuppressWarnings("java:S4502")
  @Bean
  @Order(3)
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      PermissionJwtAuthenticationConverter jwtAuthenticationConverter,
      AccessDeniedHandler accessDeniedHandler,
      AuthenticationEntryPoint authenticationEntryPoint,
      CorsConfigurationSource corsConfigurationSource,
      MdcLoggingFilter mdcLoggingFilter,
      RateLimitFilter rateLimitFilter)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .headers(securityHeadersCustomizer())
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/error")
                    .permitAll()
                    .requestMatchers("/api/v1/auth/**")
                    .permitAll()
                    .requestMatchers("/api/v1/invites/accept")
                    .permitAll()
                    .requestMatchers("/internal/sentry/**", "/api/v1/internal/sentry/**")
                    .permitAll()
                    // Stripe calls this server-to-server; authenticity verified by HMAC signature.
                    .requestMatchers(HttpMethod.POST, "/api/v1/billing/webhook")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/openapi.json",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
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
        .addFilterBefore(rateLimitFilter, BearerTokenAuthenticationFilter.class)
        .addFilterAfter(new TenantContextFilter(), BearerTokenAuthenticationFilter.class)
        .addFilterAfter(mdcLoggingFilter, TenantContextFilter.class);

    return http.build();
  }
}
