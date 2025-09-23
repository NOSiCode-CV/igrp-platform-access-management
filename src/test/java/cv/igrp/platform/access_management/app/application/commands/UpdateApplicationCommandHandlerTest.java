package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.application.constants.AppType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UpdateApplicationCommandHandlerTest {

    @InjectMocks
    private UpdateApplicationCommandHandler underTest;
    @Mock
    private ApplicationEntityRepository applicationRepository;
    @Mock
    private ApplicationMapper applicationMapper;

    @Test
    void itShouldStartContext() {
        assertNotNull(underTest);
    }

    @Test
    void itShouldThrowRecordNotFoundException_When_ProvidedApplicationId_DoesNotExist() {
        //... Given
        String appCode = "APP";
        ApplicationDTO applicationDTO = new ApplicationDTO();
        UpdateApplicationCommand command = new UpdateApplicationCommand(applicationDTO, appCode);

        //... When
        IgrpResponseStatusException response = assertThrows(IgrpResponseStatusException.class, () -> underTest.handle(command));
        //... Then
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().getStatus());
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
        String applicationPreviousUrl = "https://old-url.com";
        String applicationPreviousSlug = "old-slug";

        ApplicationEntity existingApp = new ApplicationEntity();
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

        when(applicationRepository.findByCodeAndStatusNot(applicationCode, Status.DELETED)).thenReturn(Optional.of(existingApp));

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

        UpdateApplicationCommand command = new UpdateApplicationCommand(dto, applicationCode);

        ArgumentCaptor<ApplicationEntity> appCaptor = ArgumentCaptor.forClass(ApplicationEntity.class);
        when(applicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(applicationMapper.toDto(any())).thenAnswer(invocation -> {
            ApplicationEntity a = invocation.getArgument(0);
            ApplicationDTO result = new ApplicationDTO();
            result.setId(a.getId());
            result.setName(a.getName());
            result.setSlug(a.getSlug());
            return result;
        });

        // When
        underTest.handle(command);

        // Then
        verify(applicationRepository).save(appCaptor.capture());
        ApplicationEntity captured = appCaptor.getValue();

        assertEquals(applicationNewName, captured.getName());
        assertEquals(applicationNewSlug, captured.getSlug());
        assertEquals(applicationCode, captured.getCode());
        assertEquals(applicationPreviousDescription, captured.getDescription());
        assertEquals(applicationPreviousUrl, captured.getUrl());
    }

}