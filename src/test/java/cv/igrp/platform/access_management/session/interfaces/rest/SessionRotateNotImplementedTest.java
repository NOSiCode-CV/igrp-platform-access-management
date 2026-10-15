package cv.igrp.platform.access_management.session.interfaces.rest;

import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.platform.access_management.session.application.dto.SessionInitRequestDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@code POST /api/session/rotate} must answer 501 and do nothing else.
 *
 * <p>A REST implementation cannot rotate a session: the point of rotation is a
 * new {@code sid}, and only the token endpoint can put that sid into a token.
 * The implementation that used to be here closed the caller's row and opened a
 * replacement nothing was bound to — so the caller's own next request failed
 * with {@code session_revoked}, and the orphan row went on counting toward the
 * per-user cap. Reported by the frontend team against 34f9b757.
 */
class SessionRotateNotImplementedTest {

    private CommandBus commandBus;
    private SessionController controller;

    @BeforeEach
    void setUp() {
        commandBus = mock(CommandBus.class);
        AuthenticationHelper authenticationHelper = mock(AuthenticationHelper.class);
        when(authenticationHelper.getSub()).thenReturn("00000000-0000-0000-0000-000000000042");

        controller = new SessionController(mock(QueryBus.class), commandBus, authenticationHelper);
    }

    @Test
    @DisplayName("rotate answers 501")
    void rotate_isNotImplemented() {
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> controller.rotateSession(new SessionInitRequestDTO(), new MockHttpServletRequest()));

        assertEquals(HttpStatus.NOT_IMPLEMENTED.value(), ex.getStatusCode().value());
    }

    @Test
    @DisplayName("rotate touches no session state at all")
    void rotate_dispatchesNothing() {
        assertThrows(IgrpResponseStatusException.class,
                () -> controller.rotateSession(new SessionInitRequestDTO(), new MockHttpServletRequest()));

        // The whole point: no row is closed, and above all no orphan row is
        // created to sit in the user's session budget.
        verifyNoInteractions(commandBus);
    }

    @Test
    @DisplayName("the 501 explains where rotation actually happens")
    void rotate_pointsAtTheTokenEndpoint() {
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> controller.rotateSession(new SessionInitRequestDTO(), new MockHttpServletRequest()));

        String body = String.valueOf(ex.getBody());
        assertTrue(body.contains("/oauth2/token"),
                "a caller told 'not implemented' needs to know what to use instead: " + body);
    }
}
