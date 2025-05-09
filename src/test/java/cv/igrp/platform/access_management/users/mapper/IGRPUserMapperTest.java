package cv.igrp.platform.access_management.users.mapper;

import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IGRPUserMapper Tests")
class IGRPUserMapperTest {

    private IGRPUserMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new IGRPUserMapper();
    }

    @Test
    @DisplayName("toDto(): should map IGRPUser to IGRPUserDTO")
    void toDto_shouldMapAllFields() {
        // Arrange
        IGRPUser user = new IGRPUser();
        user.setId(1);
        user.setName("Jane Doe");
        user.setUsername("janedoe");
        user.setEmail("jane@example.com");

        // Act
        IGRPUserDTO dto = mapper.toDto(user);

        // Assert
        assertNotNull(dto);
        assertEquals(1, dto.getId());
        assertEquals("Jane Doe", dto.getName());
        assertEquals("janedoe", dto.getUsername());
        assertEquals("jane@example.com", dto.getEmail());
    }

    @Test
    @DisplayName("toEntity(): should map IGRPUserDTO to IGRPUser")
    void toEntity_shouldMapAllFields() {
        // Arrange
        IGRPUserDTO dto = new IGRPUserDTO();
        dto.setId(2);
        dto.setName("John Smith");
        dto.setUsername("jsmith");
        dto.setEmail("john@example.com");

        // Act
        IGRPUser user = mapper.toEntity(dto);

        // Assert
        assertNotNull(user);
        assertEquals("2", user.getId());
        assertEquals("John Smith", user.getName());
        assertEquals("jsmith", user.getUsername());
        assertEquals("john@example.com", user.getEmail());
    }

    @Test
    @DisplayName("toDto(): should return null if input is null")
    void toDto_shouldReturnNullWhenInputIsNull() {
        assertNull(mapper.toDto(null));
    }

    @Test
    @DisplayName("toEntity(): should return null if input is null")
    void toEntity_shouldReturnNullWhenInputIsNull() {
        assertNull(mapper.toEntity(null));
    }
}