package cv.igrp.platform.access_management.users.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.IGRPUserRepository;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.users.application.queries.queries.GetUsersQuery;
import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class GetUsersQueryHandler implements QueryHandler<GetUsersQuery, ResponseEntity<List<IGRPUserDTO>>> {

    private final IGRPUserRepository userRepository;
    private final IGRPUserMapper userMapper;

    public GetUsersQueryHandler(IGRPUserRepository userRepository, IGRPUserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @IgrpQueryHandler
    public ResponseEntity<List<IGRPUserDTO>> handle(GetUsersQuery query) {
        Specification<IGRPUser> spec = buildSpecification(query);
        List<IGRPUserDTO> users = userRepository.findAll(spec)
                .stream()
                .map(userMapper::toDto)
                .toList();
        return ResponseEntity.ok(users);
    }

    private Specification<IGRPUser> buildSpecification(GetUsersQuery query) {
        Specification<IGRPUser> spec = Specification.where(null);

        if (query.getApplicationid() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("application").get("id"), query.getApplicationid()));
        }

        if (query.getDepartmentid() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("department").get("id"), query.getDepartmentid()));
        }

        if (query.getName() != null && !query.getName().isEmpty()) {
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("name")), "%" + query.getName().toLowerCase() + "%"));
        }

        if (query.getUsername() != null && !query.getUsername().isEmpty()) {
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("username")), "%" + query.getUsername().toLowerCase() + "%"));
        }

        if (query.getEmail() != null && !query.getEmail().isEmpty()) {
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("email")), "%" + query.getEmail().toLowerCase() + "%"));
        }

        return spec;
    }
}
