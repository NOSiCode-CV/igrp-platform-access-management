package cv.igrp.platform.access_management.department.application.queries;

import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpErrorCode;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

import cv.igrp.platform.access_management.shared.application.dto.RoleChildHierarchyDTO;

@Component
public class GetRoleChildrenQueryHandler implements QueryHandler<GetRoleChildrenQuery, ResponseEntity<RoleChildHierarchyDTO>> {

    private static final Logger logger = LoggerFactory.getLogger(GetRoleChildrenQueryHandler.class);

    private final DepartmentEntityRepository departmentRepository;
    private final RoleEntityRepository roleRepository;

    public GetRoleChildrenQueryHandler(RoleEntityRepository roleRepository, DepartmentEntityRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
        this.roleRepository = roleRepository;
    }

    @IgrpQueryHandler
    public ResponseEntity<RoleChildHierarchyDTO> handle(GetRoleChildrenQuery query) {

        DepartmentEntity department = departmentRepository.findByCodeAndStatusNotDeleted(query.getDepartmentCode());

        if (department.getStatus() != DepartmentStatus.ACTIVE) {
            logger.warn("The department is inactive: {}", query.getDepartmentCode());
            throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_DEPARTMENT_INACTIVE, query.getDepartmentCode());
        }

        RoleEntity root = roleRepository.findByDepartmentAndCodeAndStatusNotDeleted(department, query.getRoleCode());

        return ResponseEntity.ok(buildChildrenTree(root, 0, query.getLevel()));
    }

    private RoleChildHierarchyDTO buildChildrenTree(
            RoleEntity role,
            int currentLevel,
            Integer fromLevel
    ) {
        if (role == null) return null;

        boolean includeNode = fromLevel == null || currentLevel >= fromLevel;

        RoleChildHierarchyDTO dto = includeNode ? new RoleChildHierarchyDTO(role.getCode(), role.getDepartment().getCode(), new ArrayList<>()) : null;

        if (role.getChildren() == null || role.getChildren().isEmpty()) {
            return dto;
        }

        for (RoleEntity child : role.getChildren()) {
            RoleChildHierarchyDTO childDto =
                    buildChildrenTree(child, currentLevel + 1, fromLevel);

            if (childDto != null) {
                if (dto == null) {
                    dto = childDto; // level filtering skips upper nodes
                } else {
                    if (dto.getChildren() == null) {
                        dto.setChildren(new ArrayList<>());
                    }
                    dto.getChildren().add(childDto);
                }
            }
        }

        return dto;
    }


}