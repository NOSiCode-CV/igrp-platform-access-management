package cv.igrp.platform.access_management.shared.config;

import cv.igrp.platform.access_management.m2m.domain.service.MenuEntrySyncService;
import cv.igrp.platform.access_management.m2m.domain.service.PermissionSyncService;
import cv.igrp.platform.access_management.m2m.domain.service.ResourceSyncService;
import cv.igrp.platform.access_management.shared.application.constants.MenuEntryType;
import cv.igrp.platform.access_management.shared.application.constants.ResourceType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.application.dto.ResourceDTO;
import cv.igrp.platform.access_management.shared.domain.converters.CustomFieldAttributeConverter;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Spring Boot 4 migration gate — JSON written outside the HTTP layer with
 * private mappers, whose exact bytes end up in the database.
 *
 * <p>The structural hashes decide whether an M2M sync is skipped, so a mapper
 * that orders or formats properties differently would change every stored hash.
 * The values below were recorded with Jackson 2; the Jackson 3 mappers must
 * reproduce them.
 */
class PersistedJsonFormatTest {

    @Test
    @DisplayName("Resource structural hash is stable")
    void resourceHash() {
        ResourceDTO dto = new ResourceDTO();
        dto.setName("compat-service");
        dto.setType(ResourceType.API);
        dto.setDescription("Resource for application: compat-service");

        ResourceSyncService service = Mockito.mock(ResourceSyncService.class, Mockito.CALLS_REAL_METHODS);

        assertEquals("9de2bbb54ef83cc5b449006d190d3b385b6487144e4890b0ec077601c1f93894",
                ReflectionTestUtils.<String>invokeMethod(service, "computeStructuralHash", dto));
    }

    @Test
    @DisplayName("Menu structural hash is stable")
    void menuHash() {
        MenuEntryDTO dto = new MenuEntryDTO();
        dto.setCode("MENU_A");
        dto.setName("Menu A");
        dto.setUrl("/a");
        dto.setPosition((short) 1);
        dto.setType(MenuEntryType.MENU_PAGE);

        MenuEntrySyncService service = Mockito.mock(MenuEntrySyncService.class, Mockito.CALLS_REAL_METHODS);

        assertEquals("d121ceec8285127dd25ae16d727d8e4d4d8c34149c1186a2f445d4e46b0fe733",
                ReflectionTestUtils.<String>invokeMethod(service, "computeStructuralHash", List.of(dto), true));
    }

    @Test
    @DisplayName("Permission structural hash is stable")
    void permissionHash() {
        PermissionDTO dto = new PermissionDTO();
        dto.setName("igrp.users.list");
        dto.setDescription("List users");
        dto.setStatus(Status.ACTIVE);

        PermissionSyncService service = Mockito.mock(PermissionSyncService.class, Mockito.CALLS_REAL_METHODS);

        assertEquals(DigestUtils.sha256Hex("{\"name\":\"igrp.users.list\",\"description\":\"List users\",\"status\":\"ACTIVE\"}"),
                ReflectionTestUtils.<String>invokeMethod(service, "computeStructuralHash", dto));
    }

    @Test
    @DisplayName("Custom fields keep their stored JSON form, insertion order included")
    void customFieldColumn() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("zeta", "last-alphabetically");
        attributes.put("alpha", 1);
        attributes.put("nested", Map.of("k", true));

        CustomFieldAttributeConverter converter = new CustomFieldAttributeConverter();
        String column = converter.convertToDatabaseColumn(attributes);

        assertEquals("{\"zeta\":\"last-alphabetically\",\"alpha\":1,\"nested\":{\"k\":true}}", column);
        assertEquals(attributes, converter.convertToEntityAttribute(column));
    }
}
