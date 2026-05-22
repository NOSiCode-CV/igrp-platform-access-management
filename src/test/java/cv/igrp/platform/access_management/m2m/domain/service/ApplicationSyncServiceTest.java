package cv.igrp.platform.access_management.m2m.domain.service;

import cv.igrp.platform.access_management.shared.application.constants.AppType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationSyncServiceTest {

    @Mock
    private ApplicationEntityRepository applicationRepository;

    @InjectMocks
    private ApplicationSyncService service;

    private ApplicationDTO dto;

    @BeforeEach
    void setUp() {
        dto = new ApplicationDTO();
        dto.setCode("APP_ONE");
        dto.setName("App One");
        dto.setDescription("desc");
        dto.setStatus(Status.ACTIVE);
        dto.setType(AppType.INTERNAL);
    }

    @Test
    void synchronize_NullDto_ThrowsCodeRequired() {
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> service.synchronizeApplication(null));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void synchronize_NullCode_ThrowsCodeRequired() {
        dto.setCode(null);
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> service.synchronizeApplication(dto));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void synchronize_BlankCode_ThrowsCodeRequired() {
        dto.setCode("   ");
        assertThrows(IgrpResponseStatusException.class,
                () -> service.synchronizeApplication(dto));
    }

    @Test
    void synchronize_DefaultIgrpApp_Skipped() {
        dto.setCode("APP_IGRP_CENTER");
        service.synchronizeApplication(dto);
        verify(applicationRepository, never()).findByCodeAndStatusNot(any(), any());
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void synchronize_NewApplication_Created() {
        when(applicationRepository.findByCodeAndStatusNot("APP_ONE", Status.DELETED))
                .thenReturn(Optional.empty());

        service.synchronizeApplication(dto);

        ArgumentCaptor<ApplicationEntity> captor = ArgumentCaptor.forClass(ApplicationEntity.class);
        verify(applicationRepository).save(captor.capture());
        ApplicationEntity saved = captor.getValue();
        assertEquals("APP_ONE", saved.getCode());
        assertEquals("App One", saved.getName());
        assertEquals("desc", saved.getDescription());
        assertEquals(Status.ACTIVE, saved.getStatus());
        assertEquals(AppType.INTERNAL, saved.getType());
    }

    @Test
    void synchronize_ExistingDiffers_Updated() {
        ApplicationEntity existing = new ApplicationEntity();
        existing.setCode("APP_ONE");
        existing.setName("Old Name");
        existing.setDescription("Old desc");
        existing.setStatus(Status.INACTIVE);
        existing.setType(AppType.EXTERNAL);
        when(applicationRepository.findByCodeAndStatusNot("APP_ONE", Status.DELETED))
                .thenReturn(Optional.of(existing));

        service.synchronizeApplication(dto);

        assertEquals("App One", existing.getName());
        assertEquals("desc", existing.getDescription());
        assertEquals(Status.ACTIVE, existing.getStatus());
        assertEquals(AppType.INTERNAL, existing.getType());
        verify(applicationRepository).save(existing);
    }

    @Test
    void synchronize_ExistingMatches_NotSaved() {
        ApplicationEntity existing = new ApplicationEntity();
        existing.setCode("APP_ONE");
        existing.setName("App One");
        existing.setDescription("desc");
        existing.setStatus(Status.ACTIVE);
        existing.setType(AppType.INTERNAL);
        when(applicationRepository.findByCodeAndStatusNot("APP_ONE", Status.DELETED))
                .thenReturn(Optional.of(existing));

        service.synchronizeApplication(dto);

        verify(applicationRepository, never()).save(any());
    }

    @Test
    void synchronize_RepositoryThrows_WrappedAsSyncFailed() {
        when(applicationRepository.findByCodeAndStatusNot(anyString(), any()))
                .thenThrow(new RuntimeException("DB down"));

        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> service.synchronizeApplication(dto));
        assertEquals(500, ex.getStatusCode().value());
    }
}
