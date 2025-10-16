package cv.igrp.platform.access_management.department.application.queries;

import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetMenusAvailableForDepartmentQueryHandlerTest {

  @InjectMocks
  private GetMenusAvailableForDepartmentQueryHandler handler;

  @Mock
  private MenuEntryEntityRepository menuEntryEntityRepository;

  @Mock
  private DepartmentEntityRepository departmentEntityRepository;

  @Mock
  private MenuEntryMapper menuEntryMapper;

  private MenuEntryEntity menuEntity1;
  private MenuEntryEntity menuEntity2;
  private MenuEntryDTO menuDTO1;
  private MenuEntryDTO menuDTO2;

  @BeforeEach
  void setUp() {
    // Initialize mock entities and DTOs
    menuEntity1 = new MenuEntryEntity();
    menuEntity1.setId(1);
    menuEntity1.setCode("MENU_A");
    menuEntity1.setName("Menu A");

    menuEntity2 = new MenuEntryEntity();
    menuEntity2.setId(2);
    menuEntity2.setCode("MENU_B");
    menuEntity2.setName("Menu B");

    menuDTO1 = new MenuEntryDTO();
    menuDTO1.setId(1);
    menuDTO1.setCode("MENU_A");
    menuDTO1.setName("Menu A");

    menuDTO2 = new MenuEntryDTO();
    menuDTO2.setId(2);
    menuDTO2.setCode("MENU_B");
    menuDTO2.setName("Menu B");
  }

  @Test
  void testHandle_ReturnsListOfAvailableMenus() {
    // Given a query and expected data
    String departmentCode = "DEPT_1";
    GetMenusAvailableForDepartmentQuery query = new GetMenusAvailableForDepartmentQuery(departmentCode);
    List<MenuEntryEntity> mockEntities = List.of(menuEntity1, menuEntity2);

    // Mock repository and mapper behavior
    when(departmentEntityRepository.findByCodeAndStatusNotDeleted(departmentCode)).thenReturn(new DepartmentEntity());
    when(menuEntryEntityRepository.findAvailableMenusForDepartment(departmentCode)).thenReturn(mockEntities);
    when(menuEntryMapper.toDTO(menuEntity1)).thenReturn(menuDTO1);
    when(menuEntryMapper.toDTO(menuEntity2)).thenReturn(menuDTO2);

    // When the handler is called
    ResponseEntity<List<MenuEntryDTO>> response = handler.handle(query);

    // Then verify the response
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(2, response.getBody().size());
    assertEquals(menuDTO1.getName(), response.getBody().get(0).getName());
    assertEquals(menuDTO2.getName(), response.getBody().get(1).getName());
  }

  @Test
  void testHandle_NoMenusFound_ReturnsEmptyList() {
    // Given a query for a department with no available menus
    String departmentCode = "DEPT_2";
    GetMenusAvailableForDepartmentQuery query = new GetMenusAvailableForDepartmentQuery(departmentCode);

    // Mock repository to return an empty list
    when(departmentEntityRepository.findByCodeAndStatusNotDeleted(departmentCode)).thenReturn(new DepartmentEntity());
    when(menuEntryEntityRepository.findAvailableMenusForDepartment(departmentCode)).thenReturn(Collections.emptyList());

    // When the handler is called
    ResponseEntity<List<MenuEntryDTO>> response = handler.handle(query);

    // Then verify the response
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(0, response.getBody().size());
  }
}
