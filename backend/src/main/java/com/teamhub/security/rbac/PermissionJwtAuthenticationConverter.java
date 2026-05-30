package com.teamhub.security.rbac;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Translates a validated access token into an authenticated principal whose authorities are the
 * caller's <em>permissions</em> — not their role (PRD §5.4, TASK-023).
 *
 * <p>Chosen authorization mechanism: the token only carries the {@code role} claim; this converter
 * resolves that role to its permission codes via {@link PermissionResolver} and grants one
 * authority per permission, prefixed {@code PERM_}. Controllers therefore guard methods with
 * {@code @PreAuthorize("hasAuthority('PERM_job.delete.hard')")} (a permission code) instead of
 * {@code hasRole('ADMIN')}. Adding a new role only changes seed data — never a controller.
 */
@Component
public class PermissionJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  /** Authority prefix applied to every resolved permission code. */
  public static final String AUTHORITY_PREFIX = "PERM_";

  /** JWT claim carrying the caller's role code (set by {@code JwtService}). */
  static final String ROLE_CLAIM = "role";

  private final PermissionResolver permissionResolver;

  public PermissionJwtAuthenticationConverter(PermissionResolver permissionResolver) {
    this.permissionResolver = permissionResolver;
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    String roleCode = jwt.getClaimAsString(ROLE_CLAIM);
    Set<GrantedAuthority> authorities =
        permissionResolver.resolvePermissionCodes(roleCode).stream()
            .map(code -> (GrantedAuthority) new SimpleGrantedAuthority(AUTHORITY_PREFIX + code))
            .collect(Collectors.toUnmodifiableSet());
    return new JwtAuthenticationToken(jwt, authorities);
  }
}
