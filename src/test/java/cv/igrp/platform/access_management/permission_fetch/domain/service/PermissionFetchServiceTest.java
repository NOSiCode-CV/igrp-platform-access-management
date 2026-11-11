package cv.igrp.platform.access_management.permission_fetch.domain.service;

import cv.igrp.platform.access_management.active_role.application.dto.ActiveRoleDTO.Source;
import cv.igrp.platform.access_management.active_role.domain.service.ActiveRoleService;
import cv.igrp.platform.access_management.active_role.domain.service.ActiveRoleService.ResolvedActiveRole;
import cv.igrp.platform.access_management.permission_fetch.application.dto.PermissionSetDTO;
import cv.igrp.platform.access_management.permission_fetch.infrastructure.cache.PermissionSetCache;
import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import cv.igrp.platform.access_management.shared.security.ServiceAccountTokenClaims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * v3-06 (#14): the fetch's own decisions. While Redis is down the starter calls
 * the fetch on every request, so it must still answer from the database.
 */
@ExtendWith(MockitoExtension.class)
class PermissionFetchServiceTest {

    private static final String USER_ID = "4fa69fc7-bc46-4686-b2a1-684bfa42c3aa";

    @Mock private JdbcTemplate jdbc;
    @Mock private ActiveRoleService activeRoles;
    @Mock private PermissionSetCache cache;
    @Mock private AuthenticationHelper authenticationHelper;
    @Mock private SecurityAuditService audit;

    @Test
    void whileRedisIsDownTheFetchStillAnswersAndAuditsNothing() {
        PermissionFetchService service = service();
        token(Jwt.withTokenValue("t").header("alg", "none").subject(USER_ID).build());
        activeUser();
        when(activeRoles.resolve(USER_ID, "APP")).thenReturn(Optional.of(
                new ResolvedActiveRole(7, 41, "R1", "D1", Source.STORED)));
        when(jdbc.queryForList(anyString(), eq(String.class), eq(41), eq(7))).thenReturn(List.of("svc.faturas.list"));
        when(cache.write(anyString(), anyString(), anyMap()))
                .thenThrow(new RedisConnectionFailureException("Redis down"));

        PermissionSetDTO set = service.fetch("APP");

        assertThat(set.keys()).containsExactly("svc.faturas.list");
        assertThat(set.scopes()).containsEntry("svc.faturas.list", "all");
        verifyNoInteractions(audit);
    }

    @Test
    void theFirstAccessInTheTtlIsAuditedLaterOnesAreNot() {
        PermissionFetchService service = service();
        token(Jwt.withTokenValue("t").header("alg", "none").subject(USER_ID).build());
        activeUser();
        when(activeRoles.resolve(USER_ID, "APP")).thenReturn(Optional.empty());
        when(cache.write(anyString(), anyString(), anyMap())).thenReturn(true, false);

        service.fetch("APP");
        service.fetch("APP");

        verify(audit).logEvent(any(), any(), anyMap());
    }

    @Test
    void aServiceAccountTokenWithoutAUuidSubjectIs401() {
        PermissionFetchService service = service();
        token(Jwt.withTokenValue("t").header("alg", "none").subject("legacy-client-id")
                .claim(ServiceAccountTokenClaims.CLAIM_PRINCIPAL_TYPE,
                        ServiceAccountTokenClaims.PRINCIPAL_TYPE_SERVICE_ACCOUNT)
                .build());

        assertThatThrownBy(() -> service.fetch("APP"))
                .isInstanceOfSatisfying(IgrpResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(cache, never()).write(anyString(), anyString(), anyMap());
    }

    private PermissionFetchService service() {
        return new PermissionFetchService(jdbc, activeRoles, cache, authenticationHelper, audit);
    }

    private void token(Jwt jwt) {
        when(authenticationHelper.getJwtToken()).thenReturn(jwt);
    }

    private void activeUser() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(USER_ID))).thenReturn(true);
    }
}
