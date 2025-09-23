package cv.igrp.platform.access_management.app.mapper;

import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.shared.application.constants.AppType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationMapperTest {

    @Mock
    private final ApplicationMapper underTest = Mockito.mock(ApplicationMapper.class);


    @Test
    void itShouldReturn_ApplicationWithStatus_Active_WhenNotProvided() {
        //... Given
        ApplicationDTO applicationDto = new ApplicationDTO();
        String applicationName = "App_1";
        applicationDto.setType(AppType.INTERNAL);
        applicationDto.setStatus(null);
        applicationDto.setName(applicationName);

        ApplicationEntity application = new ApplicationEntity();
        application.setType(AppType.INTERNAL);
        application.setStatus(Status.ACTIVE);
        application.setName(applicationName);

        when(underTest.toEntity(applicationDto)).thenReturn(application);

        //... When
        ApplicationEntity applicationEntity = underTest.toEntity(applicationDto);

        //... Then
        assertEquals(Status.ACTIVE, applicationEntity.getStatus());
    }

    @Test
    void itShouldReturn_ApplicationWithStatus_INACTIVE() {
        //... Given
        ApplicationDTO applicationDto = new ApplicationDTO();
        String applicationName = "App_1";
        applicationDto.setType(AppType.INTERNAL);
        applicationDto.setStatus(Status.INACTIVE);
        applicationDto.setName(applicationName);

        ApplicationEntity application = new ApplicationEntity();
        application.setType(AppType.INTERNAL);
        application.setStatus(Status.INACTIVE);
        application.setName(applicationName);

        when(underTest.toEntity(applicationDto)).thenReturn(application);

        //... When
        ApplicationEntity applicationEntity = underTest.toEntity(applicationDto);

        //... Then
        assertEquals(Status.INACTIVE, applicationEntity.getStatus());
    }
}