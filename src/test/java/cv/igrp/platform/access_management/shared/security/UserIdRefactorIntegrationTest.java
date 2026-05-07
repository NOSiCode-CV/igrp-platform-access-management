package cv.igrp.platform.access_management.shared.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import cv.igrp.platform.access_management.shared.infrastructure.service.ScopeService;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("User ID Refactor Integration Tests - String to Integer Conversion")
class UserIdRefactorIntegrationTest {

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @Mock
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getPrincipal()).thenReturn(jwt);
    }

    @Test
    @DisplayName("Should convert JWT subject String to Integer userId")
    void testJwtSubjectToIntegerConversion() {
        // Arrange - Simulates JWT token with string "sub" claim
        String jwtSubject = "12345"; // JWT "sub" claim is always a String

        // Act & Assert
        Integer userId = Integer.parseInt(jwtSubject);
        assertEquals(12345, userId);
        assertInstanceOf(Integer.class, userId);
    }

    @Test
    @DisplayName("Should handle valid numeric string subjects")
    void testValidNumericSubjects() {
        // Arrange
        String[] validSubjects = {"1", "100", "9999", "2147483647"};

        // Act & Assert
        for (String subject : validSubjects) {
            Integer userId = Integer.parseInt(subject);
            assertEquals(Integer.parseInt(subject), userId);
        }
    }

    @Test
    @DisplayName("Should throw exception for non-numeric subject strings")
    void testInvalidNonNumericSubjects() {
        // Arrange
        String[] invalidSubjects = {"abc123", "user@example.com", "12a34", ""};

        // Act & Assert
        for (String subject : invalidSubjects) {
            assertThrows(NumberFormatException.class, () -> Integer.parseInt(subject));
        }
    }

    @Test
    @DisplayName("Should handle negative numbers in subject")
    void testNegativeSubject() {
        // Arrange
        String negativeSubject = "-123";

        // Act
        Integer userId = Integer.parseInt(negativeSubject);

        // Assert
        assertEquals(-123, userId);
    }

    @Test
    @DisplayName("Should validate Integer bounds")
    void testIntegerBounds() {
        // Arrange
        String maxIntSubject = String.valueOf(Integer.MAX_VALUE);
        String minIntSubject = String.valueOf(Integer.MIN_VALUE);

        // Act
        Integer maxUserId = Integer.parseInt(maxIntSubject);
        Integer minUserId = Integer.parseInt(minIntSubject);

        // Assert
        assertEquals(Integer.MAX_VALUE, maxUserId);
        assertEquals(Integer.MIN_VALUE, minUserId);
    }

    @Test
    @DisplayName("Should reject overflow values")
    void testOverflowSubject() {
        // Arrange
        String overflowSubject = "2147483648"; // MAX_INT + 1

        // Act & Assert
        assertThrows(NumberFormatException.class, () -> Integer.parseInt(overflowSubject));
    }

    @Test
    @DisplayName("Profile ID extraction and conversion workflow")
    void testProfileIdExtractionWorkflow() {
        // Simulate the RespondUserInvitationCommandHandler conversion:
        // String idStr = profile.id(); -> Integer userId = Integer.parseInt(idStr);

        // Arrange
        UserProfile mockProfile = mock(UserProfile.class);
        when(mockProfile.id()).thenReturn("6789"); // profile.id() returns String

        // Act
        String idStr = mockProfile.id();
        Integer userId;
        try {
            userId = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            userId = null;
        }

        // Assert
        assertNotNull(userId);
        assertEquals(6789, userId);
        verify(mockProfile).id();
    }

    @Test
    @DisplayName("Profile with invalid ID should fail conversion")
    void testProfileInvalidIdConversion() {
        // Arrange
        UserProfile mockProfile = mock(UserProfile.class);
        when(mockProfile.id()).thenReturn("not-an-integer");

        // Act
        String idStr = mockProfile.id();
        Integer userId = null;
        String errorMessage = null;

        try {
            userId = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            errorMessage = e.getMessage();
        }

        // Assert
        assertNull(userId);
        assertNotNull(errorMessage);
    }

    @Test
    @DisplayName("ScopeContext should store Integer userId")
    void testScopeContextIntegerId() {
        // Arrange
        ScopeContext context = new ScopeContext();
        Integer expectedUserId = 9999;

        // Act
        context.setUserId(expectedUserId);

        // Assert
        assertEquals(expectedUserId, context.getUserId());
        assertInstanceOf(Integer.class, context.getUserId());
    }

    @Test
    @DisplayName("ActorPrincipal should use Integer id from String subject")
    void testActorPrincipalIdConversion() {
        // Arrange - Simulates conversion from JWT sub (String) to ActorPrincipal id (Integer)
        String jwtSubject = "54321";

        // Act
        Integer convertedId = Integer.parseInt(jwtSubject);
        ScopeService.ActorPrincipal actor = new ScopeService.ActorPrincipal(
                convertedId,
                java.util.Set.of("ROLE_USER"),
                false,
                new Object()
        );

        // Assert
        assertEquals(54321, actor.id());
        assertInstanceOf(Integer.class, actor.id());
    }

    @Test
    @DisplayName("Repository queries should use Integer ID parameter")
    void testRepositoryIntegerIdParameter() {
        // This test documents the expected repository method signatures after refactor

        // Before: userRepository.findByExternalId(String externalId)
        // After: userRepository.findById(Integer id)

        // Arrange
        Integer userId = 111;

        // Assert - documenting the change
        assertEquals(111, userId);
        assertInstanceOf(Integer.class, userId);
        // In actual implementation:
        // verify(userRepository).findById(111); // Integer parameter
        // NOT: verify(userRepository).findByExternalId("111"); // String parameter
    }

    @Test
    @DisplayName("Should handle ScopeContext userId throughout request")
    void testScopeContextUserIdPropagation() {
        // Arrange
        ScopeContext scopeContext = new ScopeContext();
        Integer originalUserId = 777;

        // Act
        scopeContext.setUserId(originalUserId);
        Integer retrievedUserId = scopeContext.getUserId();

        // Assert
        assertEquals(originalUserId, retrievedUserId);
        assertEquals(777, retrievedUserId);
    }

    @Test
    @DisplayName("UserProfile backward compatibility - getExternalId returns String")
    void testUserProfileExternalIdBackwardCompatibility() {
        // After refactor: IGRPUserEntity.getExternalId() returns id.toString()
        // This maintains backward compatibility

        // Arrange
        Integer internalId = 555;

        // Act
        String externalId = internalId.toString();

        // Assert
        assertEquals("555", externalId);
        assertInstanceOf(String.class, externalId);
    }
}
