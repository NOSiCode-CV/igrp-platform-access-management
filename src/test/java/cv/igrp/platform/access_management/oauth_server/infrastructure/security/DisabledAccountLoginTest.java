package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.UserIdentityEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.UserIdentityJpaRepository;
import cv.igrp.platform.access_management.session.infrastructure.audit.SessionAuditLogger;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * A disabled (INACTIVE) account must be refused at federated login with a
 * usable reason — not crash on a unique constraint.
 *
 * <p>Production, 2026-10-19: {@code fidel.luz@nosi.cv} hit
 * {@code duplicate key value violates unique constraint
 * "uk_user_identity_provider_sub"}. Two pieces disagreed about INACTIVE:
 * the login lookup hides it, so the user fell through to provisioning; the
 * anonymiser frees the constraint for DELETED only, by design. The INSERT then
 * collided with the user's own identity row.
 *
 * <p>Refusing the login is the fix rather than freeing the constraint, which
 * would have handed a disabled user a fresh working account.
 */
class DisabledAccountLoginTest {

    private static final String PROVIDER = "external-idp";
    private static final String SUB = "fidel.luz@nosi.cv";

    private UserIdentityJpaRepository identityRepository;
    private IGRPUserEntityRepository userRepository;
    private IgrpOidcUserService service;

    @BeforeEach
    void setUp() {
        identityRepository = mock(UserIdentityJpaRepository.class);
        userRepository = mock(IGRPUserEntityRepository.class);
        service = new IgrpOidcUserService(identityRepository, userRepository, mock(SessionAuditLogger.class));
    }

    private void identityLinkedTo(Status status) {
        IGRPUserEntity user = new IGRPUserEntity();
        user.setId("00000000-0000-0000-0000-000000000042");
        user.setStatus(status);

        UserIdentityEntity identity = new UserIdentityEntity();
        identity.setProvider(PROVIDER);
        identity.setUserId(SUB);
        identity.setUser(user);

        when(identityRepository.findByProviderAndUserId(PROVIDER, SUB)).thenReturn(Optional.of(identity));
    }

    private void enforce() {
        ReflectionTestUtils.invokeMethod(service, "enforceAccountNotDisabled", PROVIDER, SUB);
    }

    @Test
    @DisplayName("a disabled account is refused with account_disabled")
    void disabledAccount_isRefused() {
        identityLinkedTo(Status.INACTIVE);

        OAuth2AuthenticationException ex = assertThrows(OAuth2AuthenticationException.class, this::enforce);

        assertEquals("account_disabled", ex.getError().getErrorCode(),
                "\"disabled\", not \"suspended\" — INACTIVE covers leaving for another employer, "
                        + "a mission posting or temporary unavailability, none of which are misconduct");
    }

    @Test
    @DisplayName("the refusal never reaches provisioning, so no duplicate INSERT is attempted")
    void disabledAccount_doesNotProvision() {
        identityLinkedTo(Status.INACTIVE);

        assertThrows(OAuth2AuthenticationException.class, this::enforce);

        verify(userRepository, never()).save(any());
        verify(identityRepository, never()).save(any());
    }

    @Test
    @DisplayName("DELETED still falls through — re-login provisions a fresh account")
    void deletedAccount_fallsThrough() {
        identityLinkedTo(Status.DELETED);

        assertDoesNotThrow(this::enforce);
    }

    @Test
    @DisplayName("an active account is unaffected")
    void activeAccount_passes() {
        identityLinkedTo(Status.ACTIVE);

        assertDoesNotThrow(this::enforce);
    }

    @Test
    @DisplayName("a first-time login with no identity row passes")
    void unknownSubject_passes() {
        when(identityRepository.findByProviderAndUserId(anyString(), anyString())).thenReturn(Optional.empty());

        assertDoesNotThrow(this::enforce);
    }

    @Test
    @DisplayName("the raw lookup is used — the login-safe one hides the very row we need to see")
    void usesTheUnfilteredLookup() {
        identityLinkedTo(Status.INACTIVE);

        assertThrows(OAuth2AuthenticationException.class, this::enforce);

        verify(identityRepository).findByProviderAndUserId(PROVIDER, SUB);
        verify(identityRepository, never()).findActiveByProviderAndUserId(anyString(), anyString());
    }
}
