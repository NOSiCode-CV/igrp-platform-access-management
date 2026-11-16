package cv.igrp.platform.access_management.permission_fetch.infrastructure;

import cv.igrp.framework.auth.core.route.PermissionLookup;
import cv.igrp.platform.access_management.permission_fetch.application.dto.PermissionSetDTO;
import cv.igrp.platform.access_management.permission_fetch.domain.service.PermissionFetchService;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The in-process answer to the starter's permission fetch (#36): the starter's
 * lookup, given this client, decides exactly as it would against AM over HTTP.
 */
class InProcessPermissionFetchTest {

    private static final String APP = "APP_IGRP_CENTER";
    private static final String KEY = "access-management-api.departments.list";

    private final PermissionFetchService fetches = mock(PermissionFetchService.class);
    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private PermissionLookup lookup;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ObjectProvider<PermissionFetchService> fetchProvider = mock(ObjectProvider.class);
        ObjectProvider<JwtDecoder> decoderProvider = mock(ObjectProvider.class);
        when(fetchProvider.getObject()).thenReturn(fetches);
        when(decoderProvider.getObject()).thenReturn(decoder);
        lookup = new PermissionLookup(null, new InProcessPermissionFetch(fetchProvider, decoderProvider).client(), APP);
        Instant now = Instant.now();
        when(decoder.decode("token")).thenReturn(new Jwt("token", now, now.plusSeconds(60),
                Map.of("alg", "RS256"), Map.of("sub", "u1")));
    }

    @Test
    @DisplayName("A held key is allowed, with the set computed in-process")
    void allowsAHeldKey() {
        when(fetches.fetch(any(Jwt.class), eq(APP)))
                .thenReturn(new PermissionSetDTO(List.of(KEY), Map.of(KEY, "department"), List.of("R1"), List.of("D1")));

        assertThat(lookup.check("u1", "token", KEY)).isEqualTo(PermissionLookup.Answer.ALLOWED);
        assertThat(lookup.permissions("u1", "token")).containsExactly(KEY);
    }

    @Test
    @DisplayName("A key the set lacks is denied")
    void deniesAMissingKey() {
        when(fetches.fetch(any(Jwt.class), eq(APP))).thenReturn(new PermissionSetDTO(List.of(), Map.of(), List.of(), List.of()));

        assertThat(lookup.check("u1", "token", KEY)).isEqualTo(PermissionLookup.Answer.DENIED);
    }

    @Test
    @DisplayName("A caller AM no longer authorizes (401 from the fetch) is unauthenticated")
    void unauthorizedCaller() {
        when(fetches.fetch(any(Jwt.class), eq(APP)))
                .thenThrow(IgrpResponseStatusException.of(HttpStatus.UNAUTHORIZED, "The user is not active"));

        assertThat(lookup.check("u1", "token", KEY)).isEqualTo(PermissionLookup.Answer.UNAUTHENTICATED);
    }

    @Test
    @DisplayName("A token that does not decode is unauthenticated")
    void undecodableToken() {
        when(decoder.decode("bad")).thenThrow(new BadJwtException("bad"));

        assertThat(lookup.check("u1", "bad", KEY)).isEqualTo(PermissionLookup.Answer.UNAUTHENTICATED);
    }

    @Test
    @DisplayName("Any other failure is unavailable (503), never allowed")
    void failureIsUnavailable() {
        when(fetches.fetch(any(Jwt.class), eq(APP))).thenThrow(new IllegalStateException("database down"));

        assertThat(lookup.check("u1", "token", KEY)).isEqualTo(PermissionLookup.Answer.UNAVAILABLE);
    }

    @Test
    @DisplayName("A call without an application is 404, one without a bearer 401")
    @SuppressWarnings("unchecked")
    void answersOnlyTheFetch() {
        ObjectProvider<PermissionFetchService> fetchProvider = mock(ObjectProvider.class);
        ObjectProvider<JwtDecoder> decoderProvider = mock(ObjectProvider.class);
        var client = new InProcessPermissionFetch(fetchProvider, decoderProvider).client();

        assertThatThrownBy(() -> client.get().uri("/api/users/me/permissions?applicationCode=" + APP)
                .retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
        assertThatThrownBy(() -> client.get().uri("/api/users/me/permissions").header("Authorization", "Bearer token")
                .retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }
}
