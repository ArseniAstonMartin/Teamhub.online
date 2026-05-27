package com.teamhub.shared;

import javax.sql.DataSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wraps the auto-configured {@link DataSource} with {@link TenantAwareDataSource} via a {@link
 * BeanPostProcessor}.
 *
 * <p>Using a BeanPostProcessor avoids replacing Spring Boot's auto-configuration (Hikari settings,
 * Flyway, health indicators) while still intercepting every connection borrow transparently. The
 * {@code static} factory method ensures the processor is instantiated early enough in the context
 * lifecycle to wrap infrastructure beans before they are used.
 */
@Configuration
public class TenantDataSourceConfig {

  @Bean
  public static BeanPostProcessor tenantAwareDataSourcePostProcessor() {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String beanName)
          throws BeansException {
        if (bean instanceof DataSource ds && !(bean instanceof TenantAwareDataSource)) {
          return new TenantAwareDataSource(ds);
        }
        return bean;
      }
    };
  }
}
