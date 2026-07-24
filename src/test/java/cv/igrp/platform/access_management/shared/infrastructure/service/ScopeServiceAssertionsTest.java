package cv.igrp.platform.access_management.shared.infrastructure.service;

import cv.igrp.platform.access_management.department.domain.exceptions.OutOfScopeException;
import cv.igrp.platform.access_management.department.domain.exceptions.RootDepartmentForbiddenException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import cv.igrp.platform.access_management.shared.security.RequestScopeCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the new write-side assertion methods added to {@link ScopeService}
 * for the department-scoped-management feature (Phase 1). Existing scope
 * behaviour (getVisibleDepartmentIds etc.) is exercised by the existing
 * integration tests and is not re-covered here.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScopeServiceAssertionsTest {

    private static final String SUBJECT = "11111111-1111-1111-1111-111111111111";

    private AuthenticationHelper authHelper;
    private RequestScopeCache cache;
    private DepartmentEntityRepository departmentRepository;
    private ApplicationEntityRepository applicationRepository;
    private RoleEntityRepository roleRepository;
    private JdbcTemplate jdbcTemplate;

    private ScopeService scopeService;

    @BeforeEach
    void setUp() {
        authHelper = mock(AuthenticationHelper.class);
        cache = mock(RequestScopeCache.class);
        departmentRepository = mock(DepartmentEntityRepository.class);
        applicationRepository = mock(ApplicationEntityRepository.class);
        roleRepository = mock(RoleEntityRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);

        when(authHelper.getSub()).thenReturn(SUBJECT);

        // A minimal auth is required — several ScopeService methods check
        // SecurityContextHolder before doing anything else.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(SUBJECT, "n/a"));

        scopeService = new ScopeService(authHelper, cache, departmentRepository,
                applicationRepository, roleRepository, jdbcTemplate);
    }

    // ─── superadmin ────────────────────────────────────────────────────

    @Test
    @DisplayName("superadmin — isInScope always true; assertInScope never throws; assertSuperAdmin passes")
    void superadmin_hasUnboundedScope() {
        stubSuperAdmin(true);

        assertThat(scopeService.isInScope(42)).isTrue();
        assertThat(scopeService.isInScope(999_999)).isTrue();
        scopeService.assertInScope(1);   // no throw
        scopeService.assertSuperAdmin(); // no throw
    }

    // ─── scoped manager ────────────────────────────────────────────────

    @Test
    @DisplayName("scoped user — isInScope reads visibleDepartments set")
    void scopedUser_isInScope_readsVisibleSet() {
        stubSuperAdmin(false);
        when(cache.getVisibleDepartments()).thenReturn(Set.of(10, 11, 12));

        assertThat(scopeService.isInScope(10)).isTrue();
        assertThat(scopeService.isInScope(11)).isTrue();
        assertThat(scopeService.isInScope(99)).isFalse();
    }

    @Test
    @DisplayName("scoped user — assertInScope throws OutOfScopeException carrying the target id")
    void scopedUser_assertInScope_throws() {
        stubSuperAdmin(false);
        when(cache.getVisibleDepartments()).thenReturn(Set.of(10));

        scopeService.assertInScope(10); // in scope — passes

        assertThatThrownBy(() -> scopeService.assertInScope(99))
                .isInstanceOf(OutOfScopeException.class)
                .extracting("departmentId").isEqualTo(99);
    }

    @Test
    @DisplayName("scoped user — assertSuperAdmin throws RootDepartmentForbiddenException")
    void scopedUser_assertSuperAdmin_throws() {
        stubSuperAdmin(false);

        assertThatThrownBy(() -> scopeService.assertSuperAdmin())
                .isInstanceOf(RootDepartmentForbiddenException.class);
    }

    // ─── edge cases ────────────────────────────────────────────────────

    @Test
    @DisplayName("null departmentId — always false, always throws in assertInScope")
    void nullDepartmentId_neverInScope() {
        stubSuperAdmin(false);

        assertThat(scopeService.isInScope(null)).isFalse();
        assertThatThrownBy(() -> scopeService.assertInScope(null))
                .isInstanceOf(OutOfScopeException.class)
                .extracting("departmentId").isNull();
    }

    @Test
    @DisplayName("user with empty visibleDepartments — every check false, every assert throws")
    void emptyVisibleSet_neverInScope() {
        stubSuperAdmin(false);
        when(cache.getVisibleDepartments()).thenReturn(Set.of());

        assertThat(scopeService.isInScope(1)).isFalse();
        assertThatThrownBy(() -> scopeService.assertInScope(1))
                .isInstanceOf(OutOfScopeException.class);
    }

    // ─── helpers ───────────────────────────────────────────────────────

    /**
     * ScopeService#isSuperAdmin uses raw JdbcTemplate to check the
     * t_user_role_assignment table for the SUPER_ADMIN_ROLE. Stub the query
     * to return either a non-empty (superadmin) or empty (non-superadmin)
     * result.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void stubSuperAdmin(boolean isSuperAdmin) {
        List result = isSuperAdmin ? List.of(1) : List.of();
        when(jdbcTemplate.query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class),
                any(Object.class), any(Object.class)))
                .thenReturn(result);
    }
}
