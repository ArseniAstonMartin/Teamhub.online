package com.teamhub.logging;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the logging infrastructure (PRD §24.6).
 *
 * <p>Exposes {@link MdcLoggingFilter} as a managed bean so it can be injected into {@link
 * com.teamhub.security.SecurityConfig} and wired into the Spring Security filter chain after JWT
 * authentication and tenant resolution. The {@link FilterRegistrationBean} with {@code
 * setEnabled(false)} prevents Spring Boot from also registering the filter as a standalone servlet
 * filter — registration happens solely via the security chain.
 */
@Configuration
public class LoggingConfig {

  @Bean
  public MdcLoggingFilter mdcLoggingFilter() {
    return new MdcLoggingFilter();
  }

  @Bean
  public FilterRegistrationBean<MdcLoggingFilter> mdcLoggingFilterRegistration(
      MdcLoggingFilter mdcLoggingFilter) {
    FilterRegistrationBean<MdcLoggingFilter> registration =
        new FilterRegistrationBean<>(mdcLoggingFilter);
    registration.setEnabled(false);
    return registration;
  }
}
