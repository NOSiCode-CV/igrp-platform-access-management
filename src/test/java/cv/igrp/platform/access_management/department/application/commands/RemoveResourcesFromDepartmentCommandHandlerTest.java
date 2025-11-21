package cv.igrp.platform.access_management.department.application.commands;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

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

@ExtendWith(MockitoExtension.class)
public class RemoveResourcesFromDepartmentCommandHandlerTest {

    @Mock
    private DepartmentEntityRepository departmentRepository;

    @Mock
    private ResourceEntityRepository resourceRepository;

    @InjectMocks
    private RemoveResourcesFromDepartmentCommandHandler handler;

    private DepartmentEntity department;
    private ResourceEntity res1;
    private ResourceEntity res2;

    @BeforeEach
    void setUp() {
        department = new DepartmentEntity();
        department.setId(1);
        department.setCode("DEP1");
        department.setName("Department1");

        res1 = new ResourceEntity();
        res1.setName("Resource1");
        res1.setDepartments(new HashSet<>());
        res1.getDepartments().add(department);

        res2 = new ResourceEntity();
        res2.setName("Resource2");
        res2.setDepartments(new HashSet<>());
        // res2 does NOT have department initially
    }

    // ------------------ SUCCESS CASES ------------------

    @Test
    void testHandle_SingleResource_Success() {
        RemoveResourcesFromDepartmentCommand command = new RemoveResourcesFromDepartmentCommand(
                List.of("Resource1"), "DEP1"
        );

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1")).thenReturn(department);
        when(resourceRepository.findByNameNotDeleted("Resource1")).thenReturn(res1);

        ResponseEntity<String> response = handler.handle(command);

        assertNotNull(response);
        assertEquals(204, response.getStatusCode().value());
        assertFalse(res1.getDepartments().contains(department));

        verify(resourceRepository, times(1)).save(res1);
    }

    @Test
    void testHandle_MultipleResources_Success() {
        RemoveResourcesFromDepartmentCommand command = new RemoveResourcesFromDepartmentCommand(
                List.of("Resource1", "Resource2"), "DEP1"
        );

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1")).thenReturn(department);
        when(resourceRepository.findByNameNotDeleted("Resource1")).thenReturn(res1);
        when(resourceRepository.findByNameNotDeleted("Resource2")).thenReturn(res2);

        ResponseEntity<String> response = handler.handle(command);

        assertNotNull(response);
        assertEquals(204, response.getStatusCode().value());
        assertFalse(res1.getDepartments().contains(department));
        // res2 was not linked, so nothing removed
        assertFalse(res2.getDepartments().contains(department));

        verify(resourceRepository, times(1)).save(res1);
        verify(resourceRepository, times(1)).save(res2);
    }

    // ------------------ ERROR CASES ------------------

    @Test
    void testHandle_DepartmentNotFound_ShouldThrow() {
        RemoveResourcesFromDepartmentCommand command = new RemoveResourcesFromDepartmentCommand(
                List.of("Resource1"), "DEP1"
        );

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1")).thenReturn(null);

        Exception exception = assertThrows(RuntimeException.class, () -> handler.handle(command));
        // adjust RuntimeException to your IgrpResponseStatusException if used
    }

    @Test
    void testHandle_ResourceNotFound_ShouldThrow() {
        RemoveResourcesFromDepartmentCommand command = new RemoveResourcesFromDepartmentCommand(
                List.of("Resource1"), "DEP1"
        );

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1")).thenReturn(department);
        when(resourceRepository.findByNameNotDeleted("Resource1")).thenReturn(null);

        Exception exception = assertThrows(RuntimeException.class, () -> handler.handle(command));
        // adjust RuntimeException to your IgrpResponseStatusException if used
    }

    @Test
    void testHandle_ResourceNotAssigned_Ignored() {
        RemoveResourcesFromDepartmentCommand command = new RemoveResourcesFromDepartmentCommand(
                List.of("Resource2"), "DEP1"
        );

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1")).thenReturn(department);
        when(resourceRepository.findByNameNotDeleted("Resource2")).thenReturn(res2);

        ResponseEntity<String> response = handler.handle(command);

        assertNotNull(response);
        assertEquals(204, response.getStatusCode().value());
        // Department should still not be in res2's set
        assertFalse(res2.getDepartments().contains(department));

        verify(resourceRepository, times(1)).save(res2);
    }

    @Test
    void testHandle_EmptyResourceList_ShouldDoNothing() {
        RemoveResourcesFromDepartmentCommand command = new RemoveResourcesFromDepartmentCommand(
                List.of(), "DEP1"
        );

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1")).thenReturn(department);

        ResponseEntity<String> response = handler.handle(command);

        assertNotNull(response);
        assertEquals(204, response.getStatusCode().value());

        verify(resourceRepository, never()).save(any());
    }
}