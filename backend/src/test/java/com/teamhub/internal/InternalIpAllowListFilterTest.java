package com.teamhub.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class InternalIpAllowListFilterTest {

  @Mock private HttpServletRequest request;

  @Mock private HttpServletResponse response;

  @Mock private FilterChain chain;

  private InternalIpAllowListFilter filter;

  @BeforeEach
  void setUp() {
    InternalProperties properties = new InternalProperties();
    properties.setAllowedIps("127.0.0.1,::1");
    filter = new InternalIpAllowListFilter(properties);
  }

  @Test
  void allowListedIp_passesThrough() throws Exception {
    org.mockito.Mockito.when(request.getRemoteAddr()).thenReturn("127.0.0.1");

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verifyNoInteractions(response);
  }

  @Test
  void nonAllowListedIp_returns404() throws Exception {
    org.mockito.Mockito.when(request.getRemoteAddr()).thenReturn("203.0.113.50");

    filter.doFilter(request, response, chain);

    verify(response).sendError(HttpServletResponse.SC_NOT_FOUND);
    verifyNoInteractions(chain);
  }

  @Test
  void xForwardedFor_allowListedIp_passesThrough() throws Exception {
    org.mockito.Mockito.when(request.getHeader("X-Forwarded-For")).thenReturn("127.0.0.1");

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verifyNoInteractions(response);
  }

  @Test
  void xForwardedFor_nonAllowListedIp_returns404() throws Exception {
    org.mockito.Mockito.when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.50");

    filter.doFilter(request, response, chain);

    verify(response).sendError(HttpServletResponse.SC_NOT_FOUND);
    verifyNoInteractions(chain);
  }

  @Test
  void xForwardedFor_usesFirstHopWhenMultiple() throws Exception {
    org.mockito.Mockito.when(request.getHeader("X-Forwarded-For"))
        .thenReturn("203.0.113.50, 127.0.0.1");

    filter.doFilter(request, response, chain);

    verify(response).sendError(HttpServletResponse.SC_NOT_FOUND);
    verifyNoInteractions(chain);
  }

  @Test
  void ipv6Loopback_isAllowListed() throws Exception {
    org.mockito.Mockito.when(request.getRemoteAddr()).thenReturn("::1");

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }

  @Test
  void allowedIpSet_parsesCommaSeparatedValues() {
    InternalProperties properties = new InternalProperties();
    properties.setAllowedIps(" 127.0.0.1 , ::1 ");

    assertThat(properties.allowedIpSet()).containsExactlyInAnyOrder("127.0.0.1", "::1");
  }
}
