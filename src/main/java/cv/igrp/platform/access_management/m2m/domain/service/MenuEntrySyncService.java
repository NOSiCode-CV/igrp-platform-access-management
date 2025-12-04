package cv.igrp.platform.access_management.m2m.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cv.igrp.platform.access_management.app.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MenuEntrySyncService {

    private static final Logger logger = LoggerFactory.getLogger(MenuEntrySyncService.class);

    private final ApplicationEntityRepository applicationRepository;
    private final MenuEntryEntityRepository menuRepository;
    private final RoleEntityRepository roleRepository;
    private final MenuEntryMapper menuMapper;

    public MenuEntrySyncService(ApplicationEntityRepository applicationRepository,
                                MenuEntryEntityRepository menuRepository,
                                RoleEntityRepository roleRepository,
                                MenuEntryMapper menuMapper) {
        this.applicationRepository = applicationRepository;
        this.menuRepository = menuRepository;
        this.roleRepository = roleRepository;
        this.menuMapper = menuMapper;
    }

    @Transactional
    public void synchronizeMenuEntries(String applicationCode, List<MenuEntryDTO> menuDtos) {
        logger.info("[MenuSync] Starting synchronization for application '{}' with {} menu entries", applicationCode,
                menuDtos == null ? 0 : menuDtos.size());

        ApplicationEntity app = applicationRepository.findByCodeAndStatusNotDeleted(applicationCode);

        List<MenuEntryEntity> existingMenus = new ArrayList<>(app.getMenus());
        List<MenuEntryDTO> existingDtos = existingMenus.stream()
                .filter(Objects::nonNull)
                .map(menuMapper::toDTO)
                .sorted(Comparator.comparing(MenuEntryDTO::getCode, Comparator.nullsFirst(String::compareTo)))
                .toList();

        List<MenuEntryDTO> incoming = menuDtos == null ? List.of() : menuDtos.stream()
                .filter(Objects::nonNull)
                .map(this::normalizeDto)
                .sorted(Comparator.comparing(MenuEntryDTO::getCode, Comparator.nullsFirst(String::compareTo)))
                .toList();

        String existingHash = computeStructuralHash(existingDtos);
        String incomingHash = computeStructuralHash(incoming);

        if (incomingHash.equals(existingHash)) {
            logger.info("[MenuSync] Application '{}' menus already up-to-date.", applicationCode);
            return;
        }

        // Build lookup maps
        Map<String, MenuEntryEntity> existingByCode = existingMenus.stream()
                .filter(me -> me.getCode() != null)
                .collect(Collectors.toMap(MenuEntryEntity::getCode, Function.identity(), (a, _) -> a));

        Map<String, MenuEntryEntity> resultByCode = new HashMap<>(existingByCode);
        Set<String> incomingCodes = incoming.stream()
                .map(MenuEntryDTO::getCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // First pass: create or update without setting parent to avoid forward references
        for (MenuEntryDTO dto : incoming) {
            if (dto.getCode() == null || dto.getCode().isBlank()) {
                logger.warn("[MenuSync] Skipping menu without code: {}", dto);
                continue;
            }
            MenuEntryEntity entity = resultByCode.get(dto.getCode());
            if (entity == null) {
                entity = menuMapper.toEntity(dto);
                entity.setApplicationId(app);
            } else {
                applyUpdatableFields(entity, dto);
            }
            // Ensure status is set (default ACTIVE)
            if (entity.getStatus() == null) entity.setStatus(Status.ACTIVE);

            // Update roles by codes (if provided)
            if (dto.getRoles() != null) {
                Set<RoleEntity> newRoles = dto.getRoles().stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(roleRepository::findIdByCode)
                        .filter(Objects::nonNull)
                        .map(roleRepository::getReferenceById)
                        .collect(Collectors.toSet());
                entity.getRoles().clear();
                entity.getRoles().addAll(newRoles);
            }

            // Temporarily clear parent to resolve in a second pass by code
            entity.setParentId(null);

            resultByCode.put(dto.getCode(), entity);
        }

        // Persist creations/updates to get IDs for parent linking
        List<MenuEntryEntity> toSave = new ArrayList<>(resultByCode.values());
        menuRepository.saveAll(toSave);

        // Second pass: resolve parents by parentCode
        for (MenuEntryDTO dto : incoming) {
            if (dto.getParentCode() == null || dto.getParentCode().isBlank()) continue;
            MenuEntryEntity child = resultByCode.get(dto.getCode());
            MenuEntryEntity parent = resultByCode.get(dto.getParentCode());
            if (child != null && parent != null) {
                child.setParentId(parent);
                menuRepository.save(child);
            }
        }

        // Mark menus not present as DELETED
        for (MenuEntryEntity existing : existingMenus) {
            String code = existing.getCode();
            if (code != null && !incomingCodes.contains(code)) {
                if (existing.getStatus() != Status.DELETED) {
                    existing.setStatus(Status.DELETED);
                    menuRepository.save(existing);
                }
            }
        }

        logger.info("[MenuSync] Synchronization for application '{}' completed. Incoming: {}, Existing updated: {}.",
                applicationCode, incoming.size(), toSave.size());
    }

    private void applyUpdatableFields(MenuEntryEntity entity, MenuEntryDTO dto) {
        entity.setName(dto.getName());
        if (dto.getType() != null) entity.setType(dto.getType());
        entity.setPosition(dto.getPosition());
        entity.setIcon(dto.getIcon());
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
        entity.setTarget(dto.getTarget());
        entity.setUrl(dto.getUrl());
        entity.setPageSlug(dto.getPageSlug());
    }

    private MenuEntryDTO normalizeDto(MenuEntryDTO dto) {
        // Ensure required defaults
        if (dto.getStatus() == null) dto.setStatus(Status.ACTIVE);
        return dto;
    }

    private String computeStructuralHash(List<MenuEntryDTO> dtos) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            // Reduce to a minimal comparable structure to avoid audit fields differences
            List<Map<String, Object>> minimal = dtos.stream().map(d -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("code", d.getCode());
                m.put("name", d.getName());
                m.put("type", d.getType());
                m.put("position", d.getPosition());
                m.put("icon", d.getIcon());
                m.put("status", d.getStatus());
                m.put("target", d.getTarget());
                m.put("url", d.getUrl());
                m.put("pageSlug", d.getPageSlug());
                m.put("parentCode", d.getParentCode());
                List<String> roles = d.getRoles() == null ? List.of() : d.getRoles().stream().sorted().toList();
                m.put("roles", roles);
                return m;
            }).toList();
            String canonicalJson = mapper.writeValueAsString(minimal);
            return DigestUtils.sha256Hex(canonicalJson);
        } catch (Exception e) {
            throw new RuntimeException("Error computing menus hash", e);
        }
    }
}
