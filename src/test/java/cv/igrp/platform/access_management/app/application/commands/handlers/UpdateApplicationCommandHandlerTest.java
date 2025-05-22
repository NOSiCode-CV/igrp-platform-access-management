package cv.igrp.platform.access_management.app.application.commands.handlers;

import cv.igrp.platform.access_management.app.application.commands.commands.UpdateApplicationCommand;
import cv.igrp.platform.access_management.app.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.application.constants.AppType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UpdateApplicationCommandHandlerTest {

    @InjectMocks
    private UpdateApplicationCommandHandler underTest;
    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private ApplicationMapper applicationMapper;

    @Test
    void itShouldStartContext() {
        assertNotNull(underTest);
    }

    @Test
    void itShouldThrowRecordNotFoundException_When_ProvidedApplicationId_DoesNotExist() {
        //... Given
        int applicationId = 100;
        ApplicationDTO applicationDTO = new ApplicationDTO();
        UpdateApplicationCommand command = new UpdateApplicationCommand(applicationDTO, applicationId);
        when(applicationRepository.findById(applicationId))
                .thenReturn(Optional.empty());

        //... When
        IgrpResponseStatusException response = assertThrows(IgrpResponseStatusException.class, () -> underTest.handle(command));
        //... Then
        assertEquals(HttpStatus.NOT_FOUND, response.getProblem().getStatus());
    }

    @Test
    void itShouldPersistOnlyModifiedFields() {
        // Given
        int applicationId = 1;
        String applicationCode = "APP001";
        String applicationPreviousName = "Old Name";
        String applicationPreviousDescription = "Old Description";
        String applicationNewName = "New Name";
        String applicationNewSlug = "new-slug";
        String applicationOwner = "admin";
        String applicationPreviousPicture = "logo.png";
        String applicationPreviousUrl = "http://old-url.com";
        String applicationPreviousSlug = "old-slug";

        Application existingApp = new Application();
        existingApp.setId(applicationId);
        existingApp.setCode(applicationCode);
        existingApp.setName(applicationPreviousName);
        existingApp.setDescription(applicationPreviousDescription);
        existingApp.setStatus(Status.ACTIVE);
        existingApp.setType(AppType.INTERNAL);
        existingApp.setOwner(applicationOwner);
        existingApp.setPicture(applicationPreviousPicture);
        existingApp.setUrl(applicationPreviousUrl);
        existingApp.setSlug(applicationPreviousSlug);

        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(existingApp));

        ApplicationDTO dto = new ApplicationDTO();
        dto.setId(applicationId);
        dto.setCode(applicationCode);

        dto.setName(applicationNewName);
        dto.setDescription(applicationPreviousDescription);
        dto.setStatus(Status.ACTIVE);
        dto.setType(AppType.INTERNAL);
        dto.setOwner(applicationOwner);
        dto.setPicture(applicationOwner);
        dto.setUrl(URI.create(applicationPreviousUrl));
        dto.setSlug(applicationNewSlug);

        UpdateApplicationCommand command = new UpdateApplicationCommand(dto, applicationId);

        ArgumentCaptor<Application> appCaptor = ArgumentCaptor.forClass(Application.class);
        when(applicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(applicationMapper.toDto(any())).thenAnswer(invocation -> {
            Application a = invocation.getArgument(0);
            ApplicationDTO result = new ApplicationDTO();
            result.setId(a.getId());
            result.setName(a.getName());
            result.setSlug(a.getSlug());
            return result;
        });

        // When
        ResponseEntity<ApplicationDTO> response = underTest.handle(command);

        // Then
        verify(applicationRepository).save(appCaptor.capture());
        Application captured = appCaptor.getValue();

        assertEquals(applicationNewName, captured.getName());
        assertEquals(applicationNewSlug, captured.getSlug());
        assertEquals(applicationCode, captured.getCode());
        assertEquals(applicationPreviousDescription, captured.getDescription());
        assertEquals(applicationPreviousUrl, captured.getUrl());
    }

}