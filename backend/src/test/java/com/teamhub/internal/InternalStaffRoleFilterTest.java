package com.teamhub.internal;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class InternalStaffRoleFilterTest {

  @Mock private HttpServletRequest request;

  @Mock private HttpServletResponse response;

  @Mock private FilterChain chain;

  private final InternalStaffRoleFilter filter = new InternalStaffRoleFilter();

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void teamhubStaffJwt_passesThrough() throws Exception {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("role", StaffJwtService.STAFF_ROLE_CLAIM)
            .subject("staff-id")
            .build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, Set.of()));

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verifyNoInteractions(response);
  }

  @Test
  void tenantAdminJwt_returns401() throws Exception {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("role", "ADMIN")
            .claim("tenant_id", "tenant-id")
            .subject("user-id")
            .build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, Set.of()));

    filter.doFilter(request, response, chain);

    verify(response).sendError(HttpStatus.UNAUTHORIZED.value());
    verifyNoInteractions(chain);
  }

  @Test
  void unauthenticatedRequest_passesThrough() throws Exception {
    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }
}
