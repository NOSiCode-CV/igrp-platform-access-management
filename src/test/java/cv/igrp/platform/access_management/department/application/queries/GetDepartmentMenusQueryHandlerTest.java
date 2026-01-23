package cv.igrp.platform.access_management.department.application.queries;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import cv.igrp.platform.access_management.app.mapper.MenuEntryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

@ExtendWith(MockitoExtension.class)
public class GetDepartmentMenusQueryHandlerTest {

  @Mock
  private MenuEntryEntityRepository menuRepository;

  @Mock
  private DepartmentEntityRepository departmentRepository;

  @Mock
  private MenuEntryMapper menuEntryMapper;

  @InjectMocks
  private GetDepartmentMenusQueryHandler handler;

  private DepartmentEntity department;
  private MenuEntryEntity menu1;
  private MenuEntryEntity menu2;
  private MenuEntryDTO dto1;
  private MenuEntryDTO dto2;

  @BeforeEach
  void setUp() {
    department = new DepartmentEntity();
    department.setId(1);
    department.setCode("DEP1");

    menu1 = new MenuEntryEntity();
    menu1.setCode("MENU1");

    menu2 = new MenuEntryEntity();
    menu2.setCode("MENU2");

    dto1 = new MenuEntryDTO();
    dto1.setCode("MENU1");

    dto2 = new MenuEntryDTO();
    dto2.setCode("MENU2");
  }

  // ------------------ SUCCESS CASES ------------------

  @Test
  void testHandle_AllMenus_NoFilter() {
    GetDepartmentMenusQuery query = new GetDepartmentMenusQuery(null, "DEP1", null);

    when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1")).thenReturn(department);
    when(menuRepository.findByDepartmentAndStatusNotFiltered(department.getId(), Status.DELETED.getCode(), null))
            .thenReturn(List.of(menu1, menu2));
    when(menuEntryMapper.toDTO(menu1)).thenReturn(dto1);
    when(menuEntryMapper.toDTO(menu2)).thenReturn(dto2);

    ResponseEntity<List<MenuEntryDTO>> response = handler.handle(query);

    assertNotNull(response);
    assertEquals(2, response.getBody().size());
    assertTrue(response.getBody().contains(dto1));
    assertTrue(response.getBody().contains(dto2));
  }

  @Test
  void testHandle_FilterByMenuCode() {
    GetDepartmentMenusQuery query = new GetDepartmentMenusQuery("MENU1", "DEP1", null);

    when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1")).thenReturn(department);
    when(menuRepository.findByDepartmentAndStatusNotFiltered(department.getId(), Status.DELETED.getCode(), null))
            .thenReturn(List.of(menu1, menu2));
    when(menuEntryMapper.toDTO(menu1)).thenReturn(dto1);

    ResponseEntity<List<MenuEntryDTO>> response = handler.handle(query);

    assertNotNull(response);
    assertEquals(1, response.getBody().size());
    assertEquals("MENU1", response.getBody().get(0).getCode());
  }

  @Test
  void testHandle_NoMenusAvailable() {
    GetDepartmentMenusQuery query = new GetDepartmentMenusQuery(null, "DEP1", null);

    when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1")).thenReturn(department);
    when(menuRepository.findByDepartmentAndStatusNotFiltered(department.getId(), Status.DELETED.getCode(), null))
            .thenReturn(List.of());

    ResponseEntity<List<MenuEntryDTO>> response = handler.handle(query);

    assertNotNull(response);
    assertTrue(response.getBody().isEmpty());
  }

  // ------------------ ERROR CASES ------------------

  @Test
  void testHandle_DepartmentNotFound_ShouldThrow() {
    GetDepartmentMenusQuery query = new GetDepartmentMenusQuery(null, "DEP1", null);

    when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1")).thenThrow(IgrpResponseStatusException.class);

    assertThrows(IgrpResponseStatusException.class, () -> handler.handle(query));
  }
}