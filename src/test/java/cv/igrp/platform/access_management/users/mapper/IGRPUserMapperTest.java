package cv.igrp.platform.access_management.users.mapper;

import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
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
    @DisplayName("toDto(): should map IGRPUser to IGRPUserDTO and leave NIC null when not set on the entity")
    void toDto_shouldMapAllFields() {
        // Arrange
        IGRPUserEntity user = new IGRPUserEntity();
        user.setId("00000000-0000-0000-0000-000000000001");
        user.setName("Jane Doe");
        user.setUsername("janedoe");
        user.setEmail("jane@example.com");
        user.setPhoneNumber("+1234567890");
        // Deliberately leave nic null — toDto must surface it as null rather
        // than substituting the username (previous behaviour leaked the JWT
        // sub / email out as if it were a NIC).

        // Act
        IGRPUserDTO dto = mapper.toDto(user);

        // Assert
        assertNotNull(dto);
        assertEquals("00000000-0000-0000-0000-000000000001", dto.getId());
        assertEquals("Jane Doe", dto.getName());
        assertEquals("janedoe", dto.getUsername());
        assertEquals("jane@example.com", dto.getEmail());
        assertNull(dto.getNic(), "NIC must be null when the entity has no NIC; the username is NOT a fallback");
        assertEquals("+1234567890", dto.getPhoneNumber());
    }

    @Test
    @DisplayName("toDto(): should expose NIC verbatim when the entity has one")
    void toDto_shouldExposeNicWhenPresent() {
        // Arrange
        IGRPUserEntity user = new IGRPUserEntity();
        user.setId("00000000-0000-0000-0000-000000000001");
        user.setName("Jane Doe");
        user.setUsername("janedoe");
        user.setEmail("jane@example.com");
        user.setPhoneNumber("+1234567890");
        user.setNic("1234567890123");

        // Act
        IGRPUserDTO dto = mapper.toDto(user);

        // Assert
        assertEquals("1234567890123", dto.getNic());
    }

    @Test
    @DisplayName("toEntity(): should map IGRPUserDTO to IGRPUser and leave NIC null when not set on the DTO")
    void toEntity_shouldMapAllFields() {
        // Arrange
        IGRPUserDTO dto = new IGRPUserDTO();
        dto.setId("00000000-0000-0000-0000-000000000002");
        dto.setName("John Smith");
        dto.setUsername("jsmith");
        dto.setEmail("john@example.com");
        dto.setPhoneNumber("+9876543210");
        // Deliberately leave nic null — toEntity must not substitute the
        // username (previous behaviour corrupted t_user.nic with the JWT
        // sub UUID or the email on every update that omitted nic).

        // Act
        IGRPUserEntity user = mapper.toEntity(dto);

        // Assert
        assertNotNull(user);
        assertEquals("00000000-0000-0000-0000-000000000002", user.getId());
        assertEquals("John Smith", user.getName());
        assertEquals("jsmith", user.getUsername());
        assertEquals("john@example.com", user.getEmail());
        assertNull(user.getNic(), "NIC must be null when the DTO has no NIC; the username is NOT a fallback");
        assertEquals("+9876543210", user.getPhoneNumber());
    }

    @Test
    @DisplayName("toEntity(): should persist NIC verbatim when the DTO carries one")
    void toEntity_shouldPersistNicWhenPresent() {
        // Arrange
        IGRPUserDTO dto = new IGRPUserDTO();
        dto.setId("00000000-0000-0000-0000-000000000002");
        dto.setName("John Smith");
        dto.setUsername("jsmith");
        dto.setEmail("john@example.com");
        dto.setPhoneNumber("+9876543210");
        dto.setNic("0987654321098");

        // Act
        IGRPUserEntity user = mapper.toEntity(dto);

        // Assert
        assertEquals("0987654321098", user.getNic());
    }

    @Test
    @DisplayName("toEntity(): should treat blank NIC the same as null — do not persist the empty string")
    void toEntity_shouldIgnoreBlankNic() {
        // Arrange
        IGRPUserDTO dto = new IGRPUserDTO();
        dto.setId("00000000-0000-0000-0000-000000000002");
        dto.setName("John Smith");
        dto.setUsername("jsmith");
        dto.setEmail("john@example.com");
        dto.setNic("");

        // Act
        IGRPUserEntity user = mapper.toEntity(dto);

        // Assert
        assertNull(user.getNic(),
                "Blank NIC in the request must be treated as 'no NIC' rather than persisting the empty string");
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