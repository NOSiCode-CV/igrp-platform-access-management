package cv.igrp.platform.access_management.app.mapper;

import cv.igrp.platform.access_management.app.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.shared.application.constants.AppType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class ApplicationMapperTest {

    private final ApplicationMapper underTest = new ApplicationMapper();


    @Test
    void itShouldReturn_ApplicationWithStatus_Active_WhenNotProvided() {
        //... Given
        ApplicationDTO applicationDto = new ApplicationDTO();
        String applicationName = "App_1";
        applicationDto.setType(AppType.INTERNAL);
        applicationDto.setStatus(null);
        applicationDto.setName(applicationName);

        //... When
        Application applicationEntity = underTest.toEntity(applicationDto);

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

        //... When
        Application applicationEntity = underTest.toEntity(applicationDto);

        //... Then
        assertEquals(Status.INACTIVE, applicationEntity.getStatus());
    }
}