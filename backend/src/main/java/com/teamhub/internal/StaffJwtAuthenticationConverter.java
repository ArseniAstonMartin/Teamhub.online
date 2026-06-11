package com.teamhub.internal;

import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Translates a validated staff JWT into an authenticated principal (TASK-131).
 *
 * <p>Only tokens with {@code role=TEAMHUB_STAFF} are accepted. Tenant JWTs are rejected by {@link
 * InternalStaffRoleFilter}.
 */
@Component
public class StaffJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  static final String ROLE_AUTHORITY = "ROLE_" + StaffJwtService.STAFF_ROLE_CLAIM;

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    return new JwtAuthenticationToken(jwt, Set.of(new SimpleGrantedAuthority(ROLE_AUTHORITY)));
  }
}
