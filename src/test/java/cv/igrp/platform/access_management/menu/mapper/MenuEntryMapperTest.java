package cv.igrp.platform.access_management.menu.mapper;

import cv.igrp.platform.access_management.menu.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.shared.application.constants.MenuEntryType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MenuEntryMapper Tests")
class MenuEntryMapperTest {

    private MenuEntryMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new MenuEntryMapper();
    }

    @Test
    @DisplayName("toEntity(): should map DTO to entity")
    void toEntity_shouldMapAllFieldsExceptRelations() {
        MenuEntryDTO dto = new MenuEntryDTO();
        dto.setId(1);
        dto.setName("Dashboard");
        dto.setType(MenuEntryType.MENU_PAGE);
        dto.setPosition((short) 1);
        dto.setIcon("fa-home");
        dto.setStatus(Status.ACTIVE);
        dto.setTarget("_blank");
        dto.setUrl("/dashboard");

        MenuEntryEntity entity = mapper.toEntity(dto);

        assertNotNull(entity);
        assertEquals(1, entity.getId());
        assertEquals("Dashboard", entity.getName());
        assertEquals(MenuEntryType.MENU_PAGE, entity.getType());
        assertEquals(1, entity.getPosition());
        assertEquals("fa-home", entity.getIcon());
        assertEquals(Status.ACTIVE, entity.getStatus());
        assertEquals("_blank", entity.getTarget());
        assertEquals("/dashboard", entity.getUrl());
    }

    @Test
    @DisplayName("toEntity(): should Set Status to Active When not Provided")
    void toEntity_shouldSetStatusToActive_When_NotProvided() {
        MenuEntryDTO dto = new MenuEntryDTO();
        dto.setId(1);
        dto.setName("Dashboard");
        dto.setType(MenuEntryType.MENU_PAGE);
        dto.setPosition((short) 1);
        dto.setIcon("fa-home");
        dto.setStatus(null);
        dto.setTarget("_blank");
        dto.setUrl("/dashboard");

        MenuEntryEntity entity = mapper.toEntity(dto);

        assertNotNull(entity);
        assertEquals(1, entity.getId());
        assertEquals("Dashboard", entity.getName());
        assertEquals(MenuEntryType.MENU_PAGE, entity.getType());
        assertEquals(1, entity.getPosition());
        assertEquals("fa-home", entity.getIcon());
        assertEquals(Status.ACTIVE, entity.getStatus());
        assertEquals("_blank", entity.getTarget());
        assertEquals("/dashboard", entity.getUrl());
    }

    @Test
    @DisplayName("toDTO(): should map entity to DTO including nested IDs")
    void toDTO_shouldMapEntityAndNestedObjectsToFlatDTO() {
        MenuEntryEntity entity = getMenuEntry();

        MenuEntryDTO dto = mapper.toDTO(entity);

        assertNotNull(dto);
        assertEquals(10, dto.getId());
        assertEquals("Settings", dto.getName());
        assertEquals(MenuEntryType.EXTERNAL_PAGE, dto.getType());
        assertEquals(2, dto.getPosition());
        assertEquals("fa-cog", dto.getIcon());
        assertEquals(Status.INACTIVE, dto.getStatus());
        assertEquals("_self", dto.getTarget());
        assertEquals("/settings", dto.getUrl());
        assertEquals("MENU0", dto.getParentCode());
        assertEquals("APP", dto.getApplicationCode());
    }

    @NotNull
    private static MenuEntryEntity getMenuEntry() {
        MenuEntryEntity entity = new MenuEntryEntity();
        entity.setId(10);
        entity.setName("Settings");
        entity.setType(MenuEntryType.EXTERNAL_PAGE);
        entity.setPosition((short) 2);
        entity.setIcon("fa-cog");
        entity.setStatus(Status.INACTIVE);
        entity.setTarget("_self");
        entity.setUrl("/settings");
        entity.setPermissions(new ArrayList<>());

        MenuEntryEntity parent = new MenuEntryEntity();
        parent.setId(99);
        parent.setCode("MENU0");
        entity.setParentId(parent);
        parent.setPermissions(new ArrayList<>());

        ApplicationEntity app = new ApplicationEntity();
        app.setId(5);
        app.setCode("APP");
        entity.setApplicationId(app);

        return entity;
    }

    @Test
    @DisplayName("toEntity(): should return null when DTO is null")
    void toEntity_shouldReturnNullIfDtoIsNull() {
        assertNull(mapper.toEntity(null));
    }

    @Test
    @DisplayName("toDTO(): should return null when entity is null")
    void toDTO_shouldReturnNullIfEntityIsNull() {
        assertNull(mapper.toDTO(null));
    }

    @Test
    @DisplayName("toDTO(): should not fail when nested objects are null")
    void toDTO_shouldHandleNullNestedObjectsGracefully() {
        MenuEntryEntity entity = new MenuEntryEntity();
        entity.setId(1);
        entity.setName("Help");
        entity.setApplicationId(null);
        entity.setParentId(null);
        entity.setPermissions(new ArrayList<>());

        MenuEntryDTO dto = mapper.toDTO(entity);

        assertEquals(1, dto.getId());
        assertNull(dto.getApplicationCode());
        assertNull(dto.getParentCode());
    }

}