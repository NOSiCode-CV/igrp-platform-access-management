package cv.igrp.platform.access_management.shared.infrastructure.service;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ConfigurationServiceTest {

    @Mock
    IGRPUserEntityRepository userRepository;

    @InjectMocks
    private ConfigurationService configurationService;

    private IGRPUserEntity userEntity;

    @BeforeEach
    void setUp() {
        userEntity = new IGRPUserEntity();
        userEntity.setId(1);
        userEntity.setName("iGRP Super Admin");
        userEntity.setUsername("superadmin");
        userEntity.setEmail("superadmin@igrp.cv");
        userEntity.setRoles(new ArrayList<>());
    }

    @Test
    @DisplayName("should create superadmin user")
    void testHandle_whenValidCommand_shouldCreateSuperadminUser() {
        // Arrange: simulate that the user does not exist yet
        when(userRepository.findByUsername("superadmin")).thenReturn(Optional.empty());
        when(userRepository.save(any(IGRPUserEntity.class))).thenReturn(userEntity);

        // Act
        configurationService.createSuperAdminUser();

        // Assert
        verify(userRepository, times(1)).findByUsername("superadmin");
        verify(userRepository, times(1)).save(any(IGRPUserEntity.class));
        verifyNoMoreInteractions(userRepository); // removed userMapper
    }
}
