package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests AddResourcesToDepartmentCommandHandler across multiple scenarios:
 * - success
 * - department not found
 * - resource not found
 * - resource already associated with department
 */
@ExtendWith(MockitoExtension.class)
public class AddResourcesToDepartmentCommandHandlerTest {

    @Mock
    private DepartmentEntityRepository departmentRepository;

    @Mock
    private ResourceEntityRepository resourceRepository;

    @InjectMocks
    private AddResourcesToDepartmentCommandHandler handler;

    private DepartmentEntity department;

    @BeforeEach
    void setUp() {
        department = new DepartmentEntity();
        department.setId(10);
        department.setCode("DEP1");
        department.setName("Department One");
    }

    @Test
    void testHandle_Success() {
        AddResourcesToDepartmentCommand command =
                new AddResourcesToDepartmentCommand(
                        List.of("res.A", "res.B"),
                        "DEP1"
                );

        ResourceEntity resA = new ResourceEntity();
        resA.setName("res.A");
        resA.setDepartments(new HashSet<>());

        ResourceEntity resB = new ResourceEntity();
        resB.setName("res.B");
        resB.setDepartments(new HashSet<>());

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1"))
                .thenReturn(department);

        when(resourceRepository.findByNameNotDeleted("res.A"))
                .thenReturn(resA);

        when(resourceRepository.findByNameNotDeleted("res.B"))
                .thenReturn(resB);

        ResponseEntity<String> response = handler.handle(command);

        assertNotNull(response);
        assertEquals(204, response.getStatusCode().value());

        assertTrue(resA.getDepartments().contains(department));
        assertTrue(resB.getDepartments().contains(department));

        verify(resourceRepository).save(resA);
        verify(resourceRepository).save(resB);
    }

    @Test
    void testHandle_DepartmentNotFound() {
        AddResourcesToDepartmentCommand command =
                new AddResourcesToDepartmentCommand(
                        List.of("res.A"),
                        "DEP1"
                );

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1"))
                .thenReturn(null);

        assertThrows(NullPointerException.class, () -> handler.handle(command));
        verify(resourceRepository, never()).save(any());
    }

    @Test
    void testHandle_ResourceNotFound() {
        AddResourcesToDepartmentCommand command =
                new AddResourcesToDepartmentCommand(
                        List.of("res.C"),
                        "DEP1"
                );

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1"))
                .thenReturn(department);

        when(resourceRepository.findByNameNotDeleted("res.C"))
                .thenReturn(null);

        assertThrows(NullPointerException.class, () -> handler.handle(command));
        verify(resourceRepository, never()).save(any());
    }

    @Test
    void testHandle_ResourceAlreadyAssigned() {
        AddResourcesToDepartmentCommand command =
                new AddResourcesToDepartmentCommand(
                        List.of("res.A"),
                        "DEP1"
                );

        ResourceEntity resA = new ResourceEntity();
        resA.setName("res.A");
        resA.setDepartments(new HashSet<>(List.of(department)));

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1"))
                .thenReturn(department);

        when(resourceRepository.findByNameNotDeleted("res.A"))
                .thenReturn(resA);

        ResponseEntity<String> response = handler.handle(command);

        assertNotNull(response);
        assertEquals(204, response.getStatusCode().value());
        assertEquals(1, resA.getDepartments().size());

        verify(resourceRepository).save(resA);
    }
}