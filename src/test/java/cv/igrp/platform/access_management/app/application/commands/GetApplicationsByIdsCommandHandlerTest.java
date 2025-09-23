package cv.igrp.platform.access_management.app.application.commands;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.application.constants.AppType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import cv.igrp.platform.access_management.shared.application.dto.*;

import java.util.Arrays;
import java.util.List;

@ExtendWith(MockitoExtension.class)
public class GetApplicationsByIdsCommandHandlerTest {

    @Mock
    private ApplicationEntityRepository applicationRepository;

    @Mock
    private ApplicationMapper applicationMapper;

    private GetApplicationsByIdsCommandHandler getApplicationsByIdsCommandHandler;

    @BeforeEach
    void setUp() {
        getApplicationsByIdsCommandHandler = new GetApplicationsByIdsCommandHandler(applicationRepository, applicationMapper);
    }

    @Test
    void testHandle_ShouldReturnMappedApplicationDTOList() {
        // Given
        ApplicationEntity app1 = new ApplicationEntity();
        app1.setId(1);
        app1.setCode("APP1");
        app1.setName("Application One");
        app1.setDescription("First App");
        app1.setStatus(Status.ACTIVE);
        app1.setType(AppType.INTERNAL);
        app1.setOwner("Owner1");
        app1.setPicture("pic1.png");
        app1.setUrl("http://app1.com");
        app1.setSlug("app-one");

        ApplicationEntity app2 = new ApplicationEntity();
        app2.setId(2);
        app2.setCode("APP2");
        app2.setName("Application Two");

        ApplicationDTO dto1 = new ApplicationDTO();
        dto1.setId(1);
        dto1.setCode("APP1");
        dto1.setName("Application One");
        dto1.setUrl(java.net.URI.create("http://app1.com"));

        ApplicationDTO dto2 = new ApplicationDTO();
        dto2.setId(2);
        dto2.setCode("APP2");
        dto2.setName("Application Two");

        List<ApplicationEntity> applicationList = Arrays.asList(app1, app2);
        List<ApplicationDTO> dtoListExpected = Arrays.asList(dto1, dto2);
        List<Integer> ids = Arrays.asList(1, 2);
        GetApplicationsByIdsCommand command = new GetApplicationsByIdsCommand(ids);

        when(applicationRepository.findByIdInAndStatusNot(ids, Status.DELETED)).thenReturn(applicationList);
        when(applicationMapper.toDto(app1)).thenReturn(dto1);
        when(applicationMapper.toDto(app2)).thenReturn(dto2);

        // When
        ResponseEntity<List<ApplicationDTO>> response = getApplicationsByIdsCommandHandler.handle(command);

        // Then
        assertNotNull(response);
        List<ApplicationDTO> dtoList = response.getBody();
        assertNotNull(dtoList);
        assertEquals(2, dtoList.size());

        ApplicationDTO dto1Result = dtoList.getFirst();
        assertEquals(dto1.getId(), dto1Result.getId());
        assertEquals(dto1.getName(), dto1Result.getName());
        assertEquals(dto1.getUrl().toString(), dto1Result.getUrl().toString());

        ApplicationDTO dto2Result = dtoList.get(1);
        assertEquals(dto2.getId(), dto2Result.getId());
        assertEquals(dto2.getName(), dto2Result.getName());

        verify(applicationRepository, times(1)).findByIdInAndStatusNot(ids, Status.DELETED);
        verify(applicationMapper).toDto(app1);
        verify(applicationMapper).toDto(app2);
    }


    @Test
    void testHandle_WithEmptyIdList_ShouldReturnEmptyDTOList() {
        // Given
        List<Integer> ids = List.of();
        GetApplicationsByIdsCommand command = new GetApplicationsByIdsCommand(ids);

        when(applicationRepository.findByIdInAndStatusNot(ids, Status.DELETED)).thenReturn(List.of());

        // When
        ResponseEntity<List<ApplicationDTO>> response = getApplicationsByIdsCommandHandler.handle(command);

        // Then
        assertNotNull(response);
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());

        verify(applicationRepository, times(1)).findByIdInAndStatusNot(ids, Status.DELETED);
    }

}