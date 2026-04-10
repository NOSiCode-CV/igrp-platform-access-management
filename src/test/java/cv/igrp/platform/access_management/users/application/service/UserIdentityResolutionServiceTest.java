package cv.igrp.platform.access_management.users.application.service;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserIdentityResolutionServiceTest {

    @Mock
    private IGRPUserEntityRepository userRepository;

    @InjectMocks
    private UserIdentityResolutionService service;

    @Test
    void resolve_or_create_returns_existing_user_when_found_by_email() {
        IGRPUserEntity existingUser = new IGRPUserEntity();
        existingUser.setEmail("user@example.com");
        existingUser.setExternalId("user@example.com");
        existingUser.setName("Test User");

        when(userRepository.findByAnyIdentifier("user@example.com", "user@example.com", null, null))
                .thenReturn(Optional.of(existingUser));

        IGRPUserEntity result = service.resolveOrCreate("user@example.com", "user@example.com", null, null, "Test User");

        assertEquals(existingUser, result);
        verify(userRepository, never()).save(any());
    }

    @Test
    void resolve_or_create_creates_new_user_when_not_found() {
        when(userRepository.findByAnyIdentifier("user@example.com", "user@example.com", null, null))
                .thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        IGRPUserEntity result = service.resolveOrCreate("user@example.com", "user@example.com", null, null, "Test User");

        assertNotNull(result);
        assertEquals("user@example.com", result.getEmail());
        assertEquals(Status.ACTIVE, result.getStatus());
        verify(userRepository, times(1)).save(any());
    }

    @Test
    void resolve_or_create_sets_username_to_email_for_keycloak_uuid_sub() {
        String externalId = "a667f627-5602-4ac2-4ac2-1fb6072c71c8";
        String email = "superadmin@example.com";

        when(userRepository.findByAnyIdentifier(email, externalId, null, null))
                .thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        IGRPUserEntity result = service.resolveOrCreate(externalId, email, null, null, "Super Admin");

        assertEquals("superadmin@example.com", result.getUsername());
    }

    @Test
    void resolve_or_create_sets_username_to_externalId_for_autentika_sub() {
        String externalId = "12345678A001B";

        when(userRepository.findByAnyIdentifier(null, externalId, "12345678A001B", null))
                .thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        IGRPUserEntity result = service.resolveOrCreate(externalId, null, "12345678A001B", null, "Claudio");

        assertEquals("12345678A001B", result.getUsername());
    }

    @Test
    void resolve_or_create_enriches_existing_user_with_missing_phone() {
        IGRPUserEntity existingUser = new IGRPUserEntity();
        existingUser.setExternalId("12345678A001B");
        existingUser.setPhoneNumber(null);

        when(userRepository.findByAnyIdentifier(null, "12345678A001B", "12345678A001B", "+2391234567"))
                .thenReturn(Optional.of(existingUser));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        IGRPUserEntity result = service.resolveOrCreate("12345678A001B", null, "12345678A001B", "+2391234567", "Claudio");

        assertEquals("+2391234567", result.getPhoneNumber());
        verify(userRepository, times(1)).save(any());
    }

    @Test
    void resolve_or_create_does_not_overwrite_existing_email() {
        IGRPUserEntity existingUser = new IGRPUserEntity();
        existingUser.setEmail("existing@example.com");
        existingUser.setName("Name");
        existingUser.setExternalId("ext-id");

        when(userRepository.findByAnyIdentifier("new@example.com", "ext-id", null, null))
                .thenReturn(Optional.of(existingUser));

        IGRPUserEntity result = service.resolveOrCreate("ext-id", "new@example.com", null, null, "Name");

        assertNotNull(result);
        assertEquals("existing@example.com", result.getEmail());
    }

    @Test
    void resolve_or_create_throws_when_all_identifiers_null() {
        assertThrows(IllegalArgumentException.class, () -> 
                service.resolveOrCreate(null, null, null, null, "Name"));
    }

    @Test
    void resolve_or_create_handles_race_condition() {
        IGRPUserEntity existingUser = new IGRPUserEntity();
        existingUser.setName("Name");

        when(userRepository.findByAnyIdentifier(any(), any(), any(), any()))
                .thenReturn(Optional.empty()) // first call before save
                .thenReturn(Optional.of(existingUser)); // second call in catch

        when(userRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        IGRPUserEntity result = service.resolveOrCreate("ext-id", "user@example.com", null, null, "Name");

        assertEquals(existingUser, result);
    }

    @Test
    void resolve_or_enrich_enriches_existing_user() {
        IGRPUserEntity existingUser = new IGRPUserEntity();
        existingUser.setName(null);

        when(userRepository.findByAnyIdentifier("user@example.com", "ext-id", null, null))
                .thenReturn(Optional.of(existingUser));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.resolveOrEnrich("ext-id", "user@example.com", null, null, "New Name");

        verify(userRepository, times(1)).save(any());
        assertEquals("New Name", existingUser.getName());
    }

    @Test
    void resolve_or_enrich_does_not_create_when_user_not_found() {
        when(userRepository.findByAnyIdentifier(any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        service.resolveOrEnrich("unknown-ext-id", null, null, null, "Name");

        verify(userRepository, never()).save(any());
    }
}
