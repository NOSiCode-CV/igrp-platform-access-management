package cv.igrp.platform.access_management.role.application.commands.handlers;

import cv.igrp.platform.access_management.role.application.commands.commands.DeleteRoleCommand;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.RoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DeleteRoleCommandHandlerTest {

    @InjectMocks
    private DeleteRoleCommandHandler underTest;

    @Mock
    private RoleRepository roleRepository;

    @Test
    void itShouldStartContext() {
        assertNotNull(underTest);
    }

    @Test
    void itShouldThrowNotFoundException_WhenProvidedRoleId_NotFound() {
        //... Given
        int roleId = 1;
        DeleteRoleCommand command = new DeleteRoleCommand(roleId);

        //... When
        when(roleRepository.findById(roleId))
                .thenReturn(Optional.empty());
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> underTest.handle(command));

        //... Then
        assertEquals(HttpStatus.NOT_FOUND, ex.getProblem().getStatus());
    }

    @Test
    void itShouldDeleteRole_WhenRoleIsFound() {
        // Given
        int roleId = 1;
        Role role = new Role();
        role.setId(roleId);
        role.setStatus(Status.ACTIVE);
        Role parenteRole = new Role();
        role.setParent(parenteRole);

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(roleRepository.save(role)).thenReturn(role);

        DeleteRoleCommand command = new DeleteRoleCommand(roleId);

        // When
        ResponseEntity<Boolean> result = underTest.handle(command);

        // Then
        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        assertTrue(result.getBody());
        assertEquals(Status.DELETED, role.getStatus());
        verify(roleRepository).save(role);
    }

    @Test
    void itShouldDeleteChildren_WhenRoleIsRoot() {
        // Given
        int roleId = 1;
        Role parenteRole = new Role();
        parenteRole.setId(roleId);
        parenteRole.setStatus(Status.ACTIVE);
        parenteRole.setParent(null);

        Role child1 = new Role();
        child1.setParent(parenteRole);
        child1.setStatus(Status.ACTIVE);
        Role child2 = new Role();
        child2.setStatus(Status.DELETED);
        child2.setParent(parenteRole);
        Role child3 = new Role();
        child3.setStatus(Status.INACTIVE);
        child3.setParent(parenteRole);

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(parenteRole));
        when(roleRepository.findByParent(parenteRole)).thenReturn(List.of(child1, child2, child3));

        DeleteRoleCommand command = new DeleteRoleCommand(roleId);

        // When
        underTest.handle(command);

        // Then
        assertEquals(Status.DELETED, parenteRole.getStatus());
        assertEquals(Status.DELETED, child1.getStatus());
        assertEquals(Status.DELETED, child2.getStatus());
        assertEquals(Status.DELETED, child3.getStatus());
        verify(roleRepository).save(parenteRole);
    }

    @Test
    void itShouldDeleteRole_WhenRoleIsNotRoot() {
        // Given
        int roleId = 1;
        Role role = new Role();
        role.setId(roleId);
        role.setStatus(Status.ACTIVE);
        role.setParent(null);

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(roleRepository.findByParent(role)).thenReturn(List.of());

        DeleteRoleCommand command = new DeleteRoleCommand(roleId);

        // When
        underTest.handle(command);

        // Then
        assertEquals(Status.DELETED, role.getStatus());
        verify(roleRepository).save(role);
    }

    @Test
    void itShouldHandleNullChildListSafely() {
        // Given
        int roleId = 1;
        Role role = new Role();
        role.setId(roleId);
        role.setStatus(Status.ACTIVE);
        role.setParent(null);

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(roleRepository.findByParent(role)).thenReturn(null);

        DeleteRoleCommand command = new DeleteRoleCommand(roleId);

        // When
        assertDoesNotThrow(() -> underTest.handle(command));

        // Then
        assertEquals(Status.DELETED, role.getStatus());
        verify(roleRepository).save(role);
    }

    @Test
    void itShouldSaveOnlyTheDeletedParentRole() {
        // Given
        Role parent = new Role();
        parent.setId(1);
        parent.setStatus(Status.ACTIVE);
        parent.setParent(null);

        Role child = new Role();
        child.setStatus(Status.ACTIVE);
        child.setParent(parent);

        when(roleRepository.findById(1)).thenReturn(Optional.of(parent));
        when(roleRepository.findByParent(parent)).thenReturn(List.of(child));

        // When
        underTest.handle(new DeleteRoleCommand(1));

        // Then
        verify(roleRepository, times(1)).save(parent);
        // Jpa Cascade is assumed
        verify(roleRepository, never()).save(child);
    }
}