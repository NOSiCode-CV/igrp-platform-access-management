package cv.igrp.platform.access_management.users.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.IGRPUserRepository;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.users.application.queries.queries.GetUsersQuery;
import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import jakarta.persistence.criteria.JoinType;
import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.persistence.criteria.Join;


@Service
public class GetUsersQueryHandler implements QueryHandler<GetUsersQuery, ResponseEntity<List<IGRPUserDTO>>> {
    private static final Logger logger = LoggerFactory.getLogger(GetUsersQueryHandler.class);
    private final IGRPUserRepository userRepository;
    private final IGRPUserMapper userMapper;

    public GetUsersQueryHandler(IGRPUserRepository userRepository, IGRPUserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @IgrpQueryHandler
    public ResponseEntity<List<IGRPUserDTO>> handle(GetUsersQuery query) {
        logger.info("Handling GetUsersQuery: applicationId={}, departmentId={}, name={}, username={}, email={}",
                query.getApplicationid(), query.getDepartmentid(), query.getName(), query.getUsername(), query.getEmail());
        Specification<IGRPUser> spec = buildSpecification(query);
        List<IGRPUserDTO> users = userRepository.findAll(spec)
                .stream()
                .map(userMapper::toDto)
                .toList();
        return ResponseEntity.ok(users);
    }

    private Specification<IGRPUser> buildSpecification(GetUsersQuery query) {
        Specification<IGRPUser> spec = Specification.where(null);

        // Filter by applicationId
        if (query.getApplicationid() != null) {
            spec = spec.and((root, q, cb) -> {
                Join<Object, Object> roleJoin = root.join("roles", JoinType.INNER);
                Join<Object, Object> departmentJoin = roleJoin.join("department", JoinType.INNER);
                return cb.equal(departmentJoin.get("applicationId"), query.getApplicationid());
            });
        }

        // Filter by departmentId
        if (query.getDepartmentid() != null) {
            spec = spec.and((root, q, cb) -> {
                Join<Object, Object> roleJoin = root.join("roles", JoinType.INNER);
                Join<Object, Object> departmentJoin = roleJoin.join("department", JoinType.INNER);
                return cb.equal(departmentJoin.get("id"), query.getDepartmentid());
            });
        }

        // Filter by name
        if (query.getName() != null && !query.getName().isEmpty()) {
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("name")), "%" + query.getName().toLowerCase() + "%"));
        }

        // Filter by username
        if (query.getUsername() != null && !query.getUsername().isEmpty()) {
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("username")), "%" + query.getUsername().toLowerCase() + "%"));
        }

        // Filter by email
        if (query.getEmail() != null && !query.getEmail().isEmpty()) {
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("email")), "%" + query.getEmail().toLowerCase() + "%"));
        }

        return spec;
    }
}