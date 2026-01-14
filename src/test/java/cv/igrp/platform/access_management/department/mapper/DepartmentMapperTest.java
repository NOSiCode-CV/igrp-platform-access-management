package cv.igrp.platform.access_management.department.mapper;

import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DepartmentMapper Tests")
class DepartmentMapperTest {

    private DepartmentMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new DepartmentMapper();
    }

    @Test
    @DisplayName("toDto(): should map entity to DTO correctly")
    void toDto1_shouldMapFieldsCorrectly() {

        DepartmentEntity parent = new DepartmentEntity();
        parent.setId(99);
        parent.setCode("NOSI");

        DepartmentEntity department = new DepartmentEntity();
        department.setId(10);
        department.setCode("HR");
        department.setName("Human Resources");
        department.setStatus(DepartmentStatus.ACTIVE);
        department.setDescription("Handles HR");
        department.setParentId(parent);

        DepartmentDTO dto = mapper.toDto(department);

        assertNotNull(dto);
        assertEquals(10, dto.getId());
        assertEquals("HR", dto.getCode());
        assertEquals("Human Resources", dto.getName());
        assertEquals("Handles HR", dto.getDescription());
        assertEquals(DepartmentStatus.ACTIVE, dto.getStatus());
        assertEquals("NOSI", dto.getParentCode());
    }

    @Test
    @DisplayName("toDto(): should return null if input is null")
    void toDto_shouldReturnNullWhenInputIsNull() {
        assertNull(mapper.toDto(null));
    }

    @Test
    @DisplayName("toEntity(): should map DTO to entity correctly")
    void toEntity_shouldMapFieldsCorrectly() {
        DepartmentDTO dto = new DepartmentDTO();
        dto.setId(20);
        dto.setCode("IT");
        dto.setName("Information Tech");
        dto.setDescription("Tech Dept");
        dto.setStatus(DepartmentStatus.INACTIVE);

        DepartmentEntity entity = mapper.toEntity(dto);

        assertNotNull(entity);
        assertEquals(20, entity.getId());
        assertEquals("IT", entity.getCode());
        assertEquals("Information Tech", entity.getName());
        assertEquals("Tech Dept", entity.getDescription());
        assertEquals(DepartmentStatus.INACTIVE, entity.getStatus());
    }

    @Test
    @DisplayName("toEntity(): should default to ACTIVE if status is null")
    void toEntity_shouldDefaultStatusWhenNull() {
        DepartmentDTO dto = new DepartmentDTO();
        dto.setCode("OPS");
        dto.setName("Operations");
        dto.setDescription("Ops dept");
        dto.setStatus(null);

        DepartmentEntity entity = mapper.toEntity(dto);

        assertEquals(DepartmentStatus.ACTIVE, entity.getStatus());
    }

    @Test
    @DisplayName("toEntity(): should return null if input is null")
    void toEntity_shouldReturnNullWhenInputIsNull() {
        assertNull(mapper.toEntity(null));
    }

    @Test
    @DisplayName("updateEntityFromDto(): should update existing entity")
    void updateEntityFromDto_shouldUpdateFields() {
        DepartmentDTO dto = new DepartmentDTO();
        dto.setName("New Dept");
        dto.setDescription("Updated desc");
        dto.setStatus(DepartmentStatus.INACTIVE);

        DepartmentEntity entity = new DepartmentEntity();
        entity.setCode("OLD");
        entity.setName("Old Dept");
        entity.setDescription("Old desc");
        entity.setStatus(DepartmentStatus.ACTIVE);

        mapper.updateEntityFromDto(dto, entity);

        assertEquals("OLD", entity.getCode()); // Code cannot be changed
        assertEquals("New Dept", entity.getName());
        assertEquals("Updated desc", entity.getDescription());
        assertEquals(DepartmentStatus.INACTIVE, entity.getStatus());
    }
}
