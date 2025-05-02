package cv.igrp.platform.access_management.users.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.IGRPUserRepository;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import cv.igrp.platform.access_management.users.application.queries.queries.GetUserRolesQuery;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GetUserRolesQueryHandler implements QueryHandler<GetUserRolesQuery, ResponseEntity<List<RoleDTO>>> {

    private final IGRPUserRepository userRepository;
    private final RoleMapper roleMapper;

    public GetUserRolesQueryHandler(IGRPUserRepository userRepository, RoleMapper roleMapper) {
        this.userRepository = userRepository;
        this.roleMapper = roleMapper;
    }

    @IgrpQueryHandler
    public ResponseEntity<List<RoleDTO>> handle(GetUserRolesQuery query) {
        IGRPUser user = userRepository.findById(query.getId())
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + query.getId()));

        List<RoleDTO> result = user.getRoles().stream()
                .map(roleMapper::mapToDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }
}
