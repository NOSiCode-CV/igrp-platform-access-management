package cv.igrp.platform.access_management.role.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.RoleRepository;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.role.application.queries.queries.GetRoleByIdQuery;
import cv.igrp.platform.access_management.role.application.dto.RoleDTO;

@Service
public class GetRoleByIdQueryHandler implements QueryHandler<GetRoleByIdQuery, ResponseEntity<RoleDTO>> {

    private final RoleRepository roleRepository;
    private final RoleMapper roleMapper;

    public GetRoleByIdQueryHandler(RoleRepository roleRepository, RoleMapper roleMapper) {
        this.roleRepository = roleRepository;
        this.roleMapper = roleMapper;
    }

    @IgrpQueryHandler
    public ResponseEntity<RoleDTO> handle(GetRoleByIdQuery query) {
        Role foundRole = roleRepository.findById(query.getId())
                .orElseThrow(() -> new IgrpResponseStatusException(
                        new IgrpProblem<>(HttpStatus.NOT_FOUND, "Get Role By Id", "Role with id: " + query.getId() + " not found.")
                ));
        RoleDTO response = roleMapper.mapToDto(foundRole);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

}