package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RemoveMenusFromDepartmentCommandHandlerTest {

    @Mock
    private MenuEntryEntityRepository menuEntryRepository;

    @Mock
    private DepartmentEntityRepository departmentEntityRepository;

    @Mock
    private ApplicationEntityRepository applicationEntityRepository;

    @InjectMocks
    private RemoveMenusFromDepartmentCommandHandler handler;

    private DepartmentEntity department;
    private MenuEntryEntity menuEntry;
    private ApplicationEntity application;

    @BeforeEach
    void setup() {
        department = new DepartmentEntity();
        department.setCode("DEP1");

        application =  new ApplicationEntity();
        application.setCode("AP1");

        menuEntry = new MenuEntryEntity();
        menuEntry.setCode("MENU1");
        menuEntry.setDepartments(new HashSet<>());

        menuEntry.getDepartments().add(department);

    }

    @Test
    void shouldRemoveMenuFromDepartmentSuccessfully() {
        RemoveMenusFromDepartmentCommand command = new RemoveMenusFromDepartmentCommand();
        command.setCode("DEP1");
        command.setApplicationCode("AP1");
        command.setRemoveMenusFromDepartmentRequest(List.of("MENU1"));

        when(departmentEntityRepository.findByCodeAndStatusNot("DEP1", DepartmentStatus.DELETED))
                .thenReturn(Optional.of(department));
        when(applicationEntityRepository.findByCodeAndStatusNot("AP1", Status.DELETED))
                .thenReturn(Optional.of(application));
        when(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(application, "MENU1", Status.DELETED))
                .thenReturn(Optional.of(menuEntry));

        ResponseEntity<String> response = handler.handle(command);

        assertEquals(204, response.getStatusCode().value());
        verify(menuEntryRepository, times(1)).save(menuEntry);
    }

    @Test
    void shouldThrowNotFoundIfDepartmentDoesNotExist() {
        RemoveMenusFromDepartmentCommand command = new RemoveMenusFromDepartmentCommand();
        command.setCode("NON_EXISTENT");
        command.setApplicationCode("AP1");
        command.setRemoveMenusFromDepartmentRequest(List.of("MENU1"));

        when(departmentEntityRepository.findByCodeAndStatusNot("NON_EXISTENT", DepartmentStatus.DELETED))
                .thenReturn(Optional.empty());

        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class, () -> handler.handle(command));
        assertEquals(404, ex.getStatusCode().value());
        verifyNoInteractions(menuEntryRepository, applicationEntityRepository);
    }

    @Test
    void shouldThrowNotFoundIfApplicationDoesNotExist() {
        RemoveMenusFromDepartmentCommand command = new RemoveMenusFromDepartmentCommand();

        command.setCode("DEP1");
        command.setApplicationCode("AP1");

        command.setRemoveMenusFromDepartmentRequest(List.of("NON_EXISTENT"));

        when(departmentEntityRepository.findByCodeAndStatusNot("DEP1", DepartmentStatus.DELETED))
                .thenReturn(Optional.of(department));
        when(applicationEntityRepository.findByCodeAndStatusNot("AP1", Status.DELETED))
            .thenReturn(Optional.empty());

        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class, () -> handler.handle(command));

        assertEquals(404, ex.getStatusCode().value());

        verifyNoInteractions(menuEntryRepository);
    }

    @Test
    void shouldThrowNotFoundIfMenuDoesNotExist() {
        RemoveMenusFromDepartmentCommand command = new RemoveMenusFromDepartmentCommand();
        command.setCode("DEP1");
        command.setApplicationCode("AP1");
        command.setRemoveMenusFromDepartmentRequest(List.of("MENU_NOT_FOUND"));

        when(departmentEntityRepository.findByCodeAndStatusNot("DEP1", DepartmentStatus.DELETED))
                .thenReturn(Optional.of(department));
        when(applicationEntityRepository.findByCodeAndStatusNot("AP1", Status.DELETED))
                .thenReturn(Optional.of(application));
        when(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(application, "MENU_NOT_FOUND", Status.DELETED))
                .thenReturn(Optional.empty());

        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class, () -> handler.handle(command));
        assertEquals(404, ex.getStatusCode().value());
        verify(menuEntryRepository, never()).save(any());
    }
}
