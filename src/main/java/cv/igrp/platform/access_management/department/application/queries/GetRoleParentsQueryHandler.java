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
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import cv.igrp.platform.access_management.shared.application.dto.RoleParentHierarchyDTO;

@Component
public class GetRoleParentsQueryHandler implements QueryHandler<GetRoleParentsQuery, ResponseEntity<RoleParentHierarchyDTO>> {

    private static final Logger logger = LoggerFactory.getLogger(GetRoleParentsQueryHandler.class);

    private final DepartmentEntityRepository departmentRepository;
    private final RoleEntityRepository roleRepository;

    public GetRoleParentsQueryHandler(RoleEntityRepository roleRepository, DepartmentEntityRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
        this.roleRepository = roleRepository;
    }

    @IgrpQueryHandler
    public ResponseEntity<RoleParentHierarchyDTO> handle(GetRoleParentsQuery query) {

        DepartmentEntity department = departmentRepository.findByCodeAndStatusNotDeleted(query.getDepartmentCode());

        if (department.getStatus() != DepartmentStatus.ACTIVE) {
            logger.warn("The department is inactive: {}", query.getDepartmentCode());
            throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_DEPARTMENT_INACTIVE, query.getDepartmentCode());
        }

        RoleEntity root = roleRepository.findByDepartmentAndCodeAndStatusNotDeleted(department, query.getRoleCode());

        return ResponseEntity.ok(buildParentsTree(root, query.getLevel()));

    }

    private static class ParentNode {
        RoleEntity role;
        int level;

        ParentNode(RoleEntity role, int level) {
            this.role = role;
            this.level = level;
        }
    }


    private List<ParentNode> collectParents(RoleEntity role) {

        List<ParentNode> chain = new ArrayList<>();

        int level = 0;
        RoleEntity current = role;

        while (current != null) {
            chain.add(new ParentNode(current, level));
            current = current.getParent();
            level++;
        }

        return chain;
    }

    public RoleParentHierarchyDTO buildParentsTree(RoleEntity role, Integer fromLevel) {

        List<ParentNode> chain = collectParents(role);

        List<ParentNode> filtered = chain.stream()
                .filter(n -> fromLevel == null || n.level >= fromLevel)
                .toList();

        if (filtered.isEmpty()) {
            return null;
        }

        RoleParentHierarchyDTO root = null;
        RoleParentHierarchyDTO current = null;

        for (ParentNode node : filtered) {

            RoleParentHierarchyDTO dto = new RoleParentHierarchyDTO(node.role.getCode(), node.role.getDepartment().getCode(), new ArrayList<>());

            if (current != null) {
                current.setParents(List.of(dto));
            } else {
                root = dto; // first node is the root
            }

            current = dto;
        }

        return root;
    }

}