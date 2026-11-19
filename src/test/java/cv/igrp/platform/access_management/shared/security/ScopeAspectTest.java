package cv.igrp.platform.access_management.shared.security;

import cv.igrp.platform.access_management.shared.infrastructure.service.ScopeService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScopeAspect Tests - Integer User ID Refactor")
class ScopeAspectTest {

    @Mock
    private ScopeService scopeService;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @InjectMocks
    private ScopeAspect scopeAspect;

    private ScopeService.ActorPrincipal actorPrincipal;
    private ScopeContext scopeContext;

    @BeforeEach
    void setUp() {
        // Setup default actor principal with Integer ID (refactored)
        Set<String> roles = new HashSet<>();
        roles.add("ADMIN");
        roles.add("DEPARTMENT.ROLE1");

        actorPrincipal = new ScopeService.ActorPrincipal(
                "00000000-0000-0000-0000-000000000123",
                roles,
                false,
                new Object()
        );

        scopeContext = new ScopeContext();
    }

    @Test
    @DisplayName("Should inject userId as Integer from ActorPrincipal into ScopeContext")
    void testInjectUserIdAsInteger() throws Throwable {
        // Arrange
        when(scopeService.getActor()).thenReturn(actorPrincipal);
        when(joinPoint.getArgs()).thenReturn(new Object[]{scopeContext});
        when(joinPoint.proceed(any())).thenReturn(null);

        // Act
        scopeAspect.applyScope(joinPoint);

        // Assert
        assertEquals("00000000-0000-0000-0000-000000000123", scopeContext.getUserId()); // Verify Integer userId is set
        assertFalse(scopeContext.isSuperAdmin());
        verify(joinPoint).proceed(any());
    }

    @Test
    @DisplayName("Should inject userId=999 for different ActorPrincipal")
    void testInjectDifferentUserId() throws Throwable {
        // Arrange
        ScopeService.ActorPrincipal anotherActor = new ScopeService.ActorPrincipal(
                "00000000-0000-0000-0000-000000000999",
                Set.of("USER"),
                false,
                new Object()
        );

        when(scopeService.getActor()).thenReturn(anotherActor);
        when(joinPoint.getArgs()).thenReturn(new Object[]{scopeContext});
        when(joinPoint.proceed(any())).thenReturn(null);

        // Act
        scopeAspect.applyScope(joinPoint);

        // Assert
        assertEquals("00000000-0000-0000-0000-000000000999", scopeContext.getUserId());
        verify(scopeService).getActor();
    }

    @Test
    @DisplayName("Should set superAdmin flag from ActorPrincipal")
    void testInjectSuperAdminFlag() throws Throwable {
        // Arrange
        ScopeService.ActorPrincipal superAdminActor = new ScopeService.ActorPrincipal(
                "00000000-0000-0000-0000-000000000777",
                Set.of("SUPER_ADMIN_ROLE"),
                true, // superAdmin flag
                new Object()
        );

        when(scopeService.getActor()).thenReturn(superAdminActor);
        when(scopeService.isSuperAdmin()).thenReturn(true);
        when(joinPoint.getArgs()).thenReturn(new Object[]{scopeContext});
        when(joinPoint.proceed(any())).thenReturn(null);

        // Act
        scopeAspect.applyScope(joinPoint);

        // Assert
        assertTrue(scopeContext.isSuperAdmin());
        assertEquals("00000000-0000-0000-0000-000000000777", scopeContext.getUserId());
    }

    @Test
    @DisplayName("Should handle multiple arguments with ScopeContext")
    void testApplyWithMultipleArgs() throws Throwable {
        // Arrange
        Object otherArg = new Object();
        Object[] args = new Object[]{otherArg, scopeContext, "some-string"};

        when(scopeService.getActor()).thenReturn(actorPrincipal);
        when(joinPoint.getArgs()).thenReturn(args);
        when(joinPoint.proceed(any())).thenReturn(null);

        // Act
        scopeAspect.applyScope(joinPoint);

        // Assert
        assertEquals("00000000-0000-0000-0000-000000000123", scopeContext.getUserId());
        verify(joinPoint).proceed(any());
    }

    @Test
    @DisplayName("Should proceed with modified args containing userId")
    void testProceededWithModifiedArgs() throws Throwable {
        // Arrange
        when(scopeService.getActor()).thenReturn(actorPrincipal);
        when(joinPoint.getArgs()).thenReturn(new Object[]{scopeContext});
        when(joinPoint.proceed(any())).thenReturn("result");

        // Act
        Object result = scopeAspect.applyScope(joinPoint);

        // Assert
        assertEquals("result", result);
        // Verify that proceed was called with modified args
        verify(joinPoint).proceed(argThat(args ->
            args.length > 0 &&
            args[0] instanceof ScopeContext &&
            "00000000-0000-0000-0000-000000000123".equals(((ScopeContext) args[0]).getUserId())
        ));
    }
}
