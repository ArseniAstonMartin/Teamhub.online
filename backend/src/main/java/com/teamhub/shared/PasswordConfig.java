package com.teamhub.shared;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Provides security-related beans shared across the application. */
@Configuration
public class PasswordConfig {

  /** BCrypt strength 12 as required by PRD §24.4. */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }
}
