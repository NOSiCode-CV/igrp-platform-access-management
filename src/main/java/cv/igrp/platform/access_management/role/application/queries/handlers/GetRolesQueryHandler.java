package cv.igrp.platform.access_management.role.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.RoleRepository;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.role.application.queries.queries.GetRolesQuery;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class GetRolesQueryHandler implements QueryHandler<GetRolesQuery, ResponseEntity<List<RoleDTO>>> {

    private final RoleRepository roleRepository;
    private final RoleMapper roleMapper;

    public GetRolesQueryHandler(RoleRepository roleRepository, RoleMapper roleMapper) {

        this.roleRepository = roleRepository;
        this.roleMapper = roleMapper;
    }

    @IgrpQueryHandler
    @Transactional(readOnly = true)
    public ResponseEntity<List<RoleDTO>> handle(GetRolesQuery query) {
        Status active = Status.ACTIVE;
        Status inactive = Status.INACTIVE;
        List<Role> allRoles = roleRepository.findByStatusIn(List.of(active, inactive));
        List<RoleDTO> collectedRole = allRoles.stream()
                .map(roleMapper::mapToDto)
                .toList();
        return new ResponseEntity<>(collectedRole, HttpStatus.OK);
    }
}