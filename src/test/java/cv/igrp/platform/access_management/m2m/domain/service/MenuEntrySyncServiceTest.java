package cv.igrp.platform.access_management.m2m.domain.service;

import cv.igrp.platform.access_management.app.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.MenuEntryType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.shared.application.dto.RoleDepartmentDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MenuEntrySyncServiceTest {

    @Mock private ApplicationEntityRepository applicationRepository;
    @Mock private MenuEntryEntityRepository menuRepository;
    @Mock private RoleEntityRepository roleRepository;
    @Mock private MenuEntryMapper menuMapper;

    @InjectMocks
    private MenuEntrySyncService service;

    private ApplicationEntity app;

    @BeforeEach
    void setUp() {
        app = new ApplicationEntity();
        app.setCode("APP1");
        app.setMenus(new ArrayList<>());
        when(applicationRepository.findByCodeAndStatusNotDeleted("APP1")).thenReturn(app);
    }

    private MenuEntryDTO dto(String code, String name, String parentCode) {
        MenuEntryDTO d = new MenuEntryDTO();
        d.setCode(code);
        d.setName(name);
        d.setType(MenuEntryType.MENU_PAGE);
        d.setStatus(Status.ACTIVE);
        d.setParentCode(parentCode);
        return d;
    }

    private MenuEntryEntity entity(String code, String name) {
        MenuEntryEntity e = new MenuEntryEntity();
        e.setCode(code);
        e.setName(name);
        e.setStatus(Status.ACTIVE);
        e.setRoles(new HashSet<>());
        return e;
    }

    @Test
    void synchronize_NullMenus_TreatsAsEmpty() {
        service.synchronizeMenuEntries("APP1", null);
        verify(menuRepository, never()).saveAll(anyList());
    }

    @Test
    void synchronize_EmptyAndNoExisting_NoOp() {
        service.synchronizeMenuEntries("APP1", List.of());
        verify(menuRepository, never()).saveAll(anyList());
    }

    @Test
    void synchronize_NewMenu_PersistedAsNewEntity() {
        MenuEntryDTO incoming = dto("M1", "Menu 1", null);
        MenuEntryEntity mapped = entity("M1", "Menu 1");
        mapped.setId(42);
        when(menuMapper.toEntity(incoming)).thenReturn(mapped);

        service.synchronizeMenuEntries("APP1", List.of(incoming));

        verify(menuRepository).saveAll(argThat((List<MenuEntryEntity> list) ->
                list.size() == 1
                && list.get(0).getCode().equals("M1")
                && list.get(0).getId() == null
                && list.get(0).getApplicationId() == app));
    }

    @Test
    void synchronize_NewMenuWithoutStatus_DefaultsActive() {
        MenuEntryDTO incoming = dto("M1", "Menu 1", null);
        incoming.setStatus(null);
        MenuEntryEntity mapped = new MenuEntryEntity();
        mapped.setCode("M1");
        mapped.setRoles(new HashSet<>());
        when(menuMapper.toEntity(incoming)).thenReturn(mapped);

        service.synchronizeMenuEntries("APP1", List.of(incoming));

        verify(menuRepository).saveAll(argThat((List<MenuEntryEntity> list) ->
                list.size() == 1 && list.get(0).getStatus() == Status.ACTIVE));
    }

    @Test
    void synchronize_BlankCode_Skipped() {
        MenuEntryDTO blank = dto("", "ignored", null);
        service.synchronizeMenuEntries("APP1", List.of(blank));
        verify(menuRepository, never()).saveAll(anyList());
    }

    @Test
    void synchronize_NullCode_Skipped() {
        MenuEntryDTO nullCode = dto(null, "ignored", null);
        service.synchronizeMenuEntries("APP1", List.of(nullCode));
        verify(menuRepository, never()).saveAll(anyList());
    }

    @Test
    void synchronize_ExistingUpToDate_NoSave() {
        MenuEntryEntity existing = entity("M1", "Menu 1");
        app.setMenus(new ArrayList<>(List.of(existing)));
        MenuEntryDTO existingDto = dto("M1", "Menu 1", null);
        when(menuMapper.toDTO(existing)).thenReturn(existingDto);

        // Incoming identical to existing
        MenuEntryDTO incoming = dto("M1", "Menu 1", null);
        service.synchronizeMenuEntries("APP1", List.of(incoming));

        verify(menuRepository, never()).saveAll(anyList());
    }

    @Test
    void synchronize_ExistingDiffers_UpdatedFields() {
        MenuEntryEntity existing = entity("M1", "Old Name");
        existing.setId(1);
        existing.setPosition((short) 0);
        app.setMenus(new ArrayList<>(List.of(existing)));
        MenuEntryDTO existingDto = dto("M1", "Old Name", null);
        when(menuMapper.toDTO(existing)).thenReturn(existingDto);

        MenuEntryDTO incoming = dto("M1", "New Name", null);
        incoming.setPosition((short) 7);
        incoming.setIcon("ic");
        incoming.setUrl("/u");
        incoming.setTarget("_blank");
        incoming.setPageSlug("slug");

        service.synchronizeMenuEntries("APP1", List.of(incoming));

        assertEquals("New Name", existing.getName());
        assertEquals((short) 7, existing.getPosition());
        assertEquals("ic", existing.getIcon());
        assertEquals("/u", existing.getUrl());
        assertEquals("_blank", existing.getTarget());
        assertEquals("slug", existing.getPageSlug());
    }

    @Test
    void synchronize_MenuMissingFromIncoming_SoftDeleted() {
        MenuEntryEntity existing = entity("M_OLD", "Stays");
        existing.setId(1);
        app.setMenus(new ArrayList<>(List.of(existing)));
        when(menuMapper.toDTO(existing)).thenReturn(dto("M_OLD", "Stays", null));

        MenuEntryDTO incoming = dto("M_NEW", "Replaces", null);
        MenuEntryEntity newMapped = entity("M_NEW", "Replaces");
        when(menuMapper.toEntity(incoming)).thenReturn(newMapped);

        service.synchronizeMenuEntries("APP1", List.of(incoming));

        assertEquals(Status.DELETED, existing.getStatus());
    }

    @Test
    void synchronize_ParentLinking_ResolvedAfterFirstPass() {
        MenuEntryDTO parent = dto("P", "Parent", null);
        MenuEntryDTO child = dto("C", "Child", "P");
        MenuEntryEntity parentEntity = entity("P", "Parent");
        MenuEntryEntity childEntity = entity("C", "Child");
        when(menuMapper.toEntity(parent)).thenReturn(parentEntity);
        when(menuMapper.toEntity(child)).thenReturn(childEntity);

        service.synchronizeMenuEntries("APP1", List.of(parent, child));

        assertSame(parentEntity, childEntity.getParentId());
    }

    @Test
    void synchronize_WithRoleSyncEnabled_RolesAssigned() {
        MenuEntryDTO incoming = dto("M1", "Menu 1", null);
        incoming.setRoles(List.of(new RoleDepartmentDTO("ROLE_A", "DEPT")));
        MenuEntryEntity mapped = entity("M1", "Menu 1");
        when(menuMapper.toEntity(incoming)).thenReturn(mapped);
        RoleEntity role = new RoleEntity();
        when(roleRepository.findByDepartmentCodeAndCodeAndStatusNot("DEPT", "ROLE_A", Status.DELETED))
                .thenReturn(Optional.of(role));

        service.synchronizeMenuEntries("APP1", List.of(incoming));

        assertTrue(mapped.getRoles().contains(role));
    }

    @Test
    void synchronize_DuplicateAdminCodes_AssignsEachDepartmentRole() {
        MenuEntryDTO incoming = dto("M1", "Menu 1", null);
        incoming.setRoles(List.of(new RoleDepartmentDTO("ADMIN", "HR"), new RoleDepartmentDTO("ADMIN", "FIN")));
        MenuEntryEntity mapped = entity("M1", "Menu 1");
        when(menuMapper.toEntity(incoming)).thenReturn(mapped);
        RoleEntity hrAdmin = new RoleEntity();
        hrAdmin.setId(2);
        RoleEntity finAdmin = new RoleEntity();
        finAdmin.setId(3);
        when(roleRepository.findByDepartmentCodeAndCodeAndStatusNot("HR", "ADMIN", Status.DELETED))
                .thenReturn(Optional.of(hrAdmin));
        when(roleRepository.findByDepartmentCodeAndCodeAndStatusNot("FIN", "ADMIN", Status.DELETED))
                .thenReturn(Optional.of(finAdmin));

        service.synchronizeMenuEntries("APP1", List.of(incoming), true);

        assertEquals(Set.of(hrAdmin, finAdmin), mapped.getRoles());
        verify(roleRepository, never()).findIdByCode(any());
    }

    @Test
    void synchronize_AdminDepartmentChanges_ReplacesRoleDespiteSameCode() {
        MenuEntryEntity existing = entity("M1", "Menu 1");
        existing.setId(1);
        RoleEntity hrAdmin = new RoleEntity();
        hrAdmin.setId(2);
        existing.getRoles().add(hrAdmin);
        app.setMenus(new ArrayList<>(List.of(existing)));
        MenuEntryDTO existingDto = dto("M1", "Menu 1", null);
        existingDto.setRoles(List.of(new RoleDepartmentDTO("ADMIN", "HR")));
        when(menuMapper.toDTO(existing)).thenReturn(existingDto);
        MenuEntryDTO incoming = dto("M1", "Menu 1", null);
        incoming.setRoles(List.of(new RoleDepartmentDTO("ADMIN", "FIN")));
        RoleEntity finAdmin = new RoleEntity();
        finAdmin.setId(3);
        when(roleRepository.findByDepartmentCodeAndCodeAndStatusNot("FIN", "ADMIN", Status.DELETED))
                .thenReturn(Optional.of(finAdmin));

        service.synchronizeMenuEntries("APP1", List.of(incoming), true);

        assertEquals(Set.of(finAdmin), existing.getRoles());
        verify(roleRepository, never()).findIdByCode(any());
    }

    @Test
    void synchronize_SameDepartmentRolePairsInDifferentOrder_NoOp() {
        MenuEntryEntity existing = entity("M1", "Menu 1");
        existing.setId(1);
        app.setMenus(new ArrayList<>(List.of(existing)));
        MenuEntryDTO existingDto = dto("M1", "Menu 1", null);
        existingDto.setRoles(List.of(new RoleDepartmentDTO("ADMIN", "HR"), new RoleDepartmentDTO("ADMIN", "FIN")));
        when(menuMapper.toDTO(existing)).thenReturn(existingDto);
        MenuEntryDTO incoming = dto("M1", "Menu 1", null);
        incoming.setRoles(List.of(new RoleDepartmentDTO("ADMIN", "FIN"), new RoleDepartmentDTO("ADMIN", "HR")));

        service.synchronizeMenuEntries("APP1", List.of(incoming), true);

        verifyNoInteractions(roleRepository);
        verify(menuRepository, never()).saveAll(anyList());
    }

    @Test
    void synchronize_RoleSyncDisabled_RolesNotTouched() {
        MenuEntryDTO incoming = dto("M1", "Menu 1", null);
        incoming.setRoles(List.of(new RoleDepartmentDTO("ROLE_A", "DEPT")));
        MenuEntryEntity mapped = entity("M1", "Menu 1");
        RoleEntity preExisting = new RoleEntity();
        mapped.getRoles().add(preExisting);
        when(menuMapper.toEntity(incoming)).thenReturn(mapped);

        service.synchronizeMenuEntries("APP1", List.of(incoming), false);

        verifyNoInteractions(roleRepository);
        assertTrue(mapped.getRoles().contains(preExisting));
    }
}
