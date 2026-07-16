package cv.igrp.platform.access_management.security_audit.application.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the actor and role resolution behind the reports.
 *
 * <p>{@code username} used to be {@code Authentication.getName()}, which for a JWT
 * is the {@code sub} claim — so every report row showed a UUID where a username
 * belongs and the documented {@code username} filter could never match
 * (requirements.md §1.5.1–§1.5.3). Resolution now mirrors
 * {@code ApplicationAuditorAware}, so an audit row and an entity's
 * {@code created_by} name the same actor the same way.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SecurityAuditContextProviderTest {

    private static final String SUB = "4fa69fc7-bc46-4686-b2a1-684bfa42c3aa";

    private final SecurityAuditContextProvider provider = new SecurityAuditContextProvider();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(Authentication authentication) {
        SecurityContextHolder.setContext(new SecurityContextImpl(authentication));
    }

    /**
     * The resource-server chain surfaces IgrpOidcUser, not a raw Jwt.
     * {@code getName()} mirrors DefaultOidcUser, which returns the {@code sub} claim
     * — the very value that used to leak into the reports as the username.
     */
    private OidcUser oidcUser(String preferredUsername, String email) {
        OidcUser user = mock(OidcUser.class);
        when(user.getPreferredUsername()).thenReturn(preferredUsername);
        when(user.getEmail()).thenReturn(email);
        when(user.getName()).thenReturn(SUB);
        return user;
    }

    private Authentication tokenFor(Object principal, String... authorities) {
        TestingAuthenticationToken token = new TestingAuthenticationToken(
                principal, null,
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
        token.setAuthenticated(true);
        return token;
    }

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "none").subject(SUB);
        claims.forEach(builder::claim);
        return builder.build();
    }

    @Test
    void usernameResolvesToPreferredUsernameNotTheSubUuid() {
        authenticate(tokenFor(oidcUser("superadmin@igrp.cv", "marcelo.monteiro@nosi.cv")));

        Map<String, Object> context = provider.getContext();

        assertThat(context.get("username")).isEqualTo("superadmin@igrp.cv");
        assertThat(context.get("username")).isNotEqualTo(SUB);
    }

    @Test
    void usernameFallsBackToEmailWhenPreferredUsernameIsAbsent() {
        authenticate(tokenFor(oidcUser(null, "marcelo.monteiro@nosi.cv")));

        assertThat(provider.getContext().get("username")).isEqualTo("marcelo.monteiro@nosi.cv");
    }

    @Test
    void usernameFallsBackToTheSubWhenNoHumanReadableClaimExists() {
        // An opaque id is still better than no actor at all.
        authenticate(tokenFor(oidcUser(null, null)));

        assertThat(provider.getContext().get("username")).isEqualTo(SUB);
    }

    @Test
    void usernameResolvesFromARawJwtPrincipal() {
        authenticate(tokenFor(jwt(Map.of("preferred_username", "alice", "email", "alice@nosi.cv"))));

        assertThat(provider.getContext().get("username")).isEqualTo("alice");
    }

    @Test
    void usernameForMachineToMachineIsTheClientId() {
        authenticate(tokenFor(new User("svc-reporting", "", List.of())));

        assertThat(provider.getContext().get("username")).isEqualTo("svc-reporting");
    }

    @Test
    void userIdRemainsTheSubClaim() {
        authenticate(tokenFor(jwt(Map.of("preferred_username", "alice"))));

        Map<String, Object> context = provider.getContext();

        assertThat(context.get("userId")).isEqualTo(SUB);
        assertThat(context.get("username")).isEqualTo("alice");
    }

    @Test
    void accessRoleIsTheActiveRoleCodesWithoutTheAuthorityPrefix() {
        authenticate(tokenFor(oidcUser("alice", null),
                "ROLE_DEPT_IGRP.Administrator", "ROLE_DEPT_IGRP.Auditor", "SESSION_MANAGEMENT"));

        // Non-role authorities are excluded; role codes are sorted for stability.
        assertThat(provider.getContext().get("accessRole"))
                .isEqualTo("DEPT_IGRP.Administrator, DEPT_IGRP.Auditor");
    }

    @Test
    void accessRoleIsNullWhenThePrincipalHoldsNoRoles() {
        authenticate(tokenFor(oidcUser("alice", null), "SESSION_MANAGEMENT"));

        assertThat(provider.getContext().get("accessRole")).isNull();
    }

    @Test
    void contextIsEmptyWhenThereIsNoAuthentication() {
        SecurityContextHolder.clearContext();

        assertThat(provider.getContext()).doesNotContainKeys("username", "userId", "accessRole");
    }
}
