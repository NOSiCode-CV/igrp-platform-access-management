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
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.persistence.criteria.Join;


/**
 * Service handler responsible for processing the {@link GetUsersQuery} and returning a filtered list
 * of users as {@link IGRPUserDTO} objects. It uses JPA Specifications to construct dynamic database queries
 * based on the required filtering parameters provided in the query.
 *
 * <p>This class is annotated with {@link Service} and implements the {@link QueryHandler} interface,
 * making it compatible with the query-handling infrastructure of the application.</p>
 *
 * <p>Filtering options include:
 * <ul>
 *     <li>Application ID (via Role → Department → Application relationship)</li>
 *     <li>Department ID (via Role → Department)</li>
 *     <li>Username (case-insensitive substring match)</li>
 *     <li>Username (case-insensitive substring match)</li>
 *     <li>Email (case-insensitive substring match)</li>
 * </ul>
 * </p>
 *
 * The handler also logs the query input parameters and ensures null-safe mapping and filtering.
 */
@Service
public class GetUsersQueryHandler implements QueryHandler<GetUsersQuery, ResponseEntity<List<IGRPUserDTO>>> {
    private static final Logger logger = LoggerFactory.getLogger(GetUsersQueryHandler.class);
    private final IGRPUserRepository userRepository;
    private final IGRPUserMapper userMapper;

    /**
     * Constructs a new {@code GetUsersQueryHandler} with the required dependencies.
     *
     * @param userRepository the repository used to access user entities
     * @param userMapper the mapper used to convert {@link IGRPUser} entities to {@link IGRPUserDTO}
     */
    public GetUsersQueryHandler(IGRPUserRepository userRepository, IGRPUserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    /**
     * Handles a {@link GetUsersQuery} by building a dynamic specification based on the query filters,
     * retrieving matching users from the repository, converting them to DTOs, and returning them
     * in a {@link ResponseEntity}.
     *
     * @param query the query containing the filters (applicationId, departmentId, name, etc.)
     * @return a {@link ResponseEntity} containing the filtered list of {@link IGRPUserDTO}
     */
    @IgrpQueryHandler
    public ResponseEntity<List<IGRPUserDTO>> handle(GetUsersQuery query) {
        logger.info("Handling GetUsersQuery: applicationId={}, departmentId={}, name={}, username={}, email={}",
                query.getApplicationId(), query.getDepartmentId(), query.getName(), query.getUsername(), query.getEmail());
        Specification<IGRPUser> spec = buildSpecification(query);
        List<IGRPUserDTO> users = userRepository.findAll(spec)
                .stream()
                .map(userMapper::toDto)
                .filter(Objects::nonNull)
                .toList();

        return ResponseEntity.ok(users);
    }

    /**
     * Builds a {@link Specification} to dynamically filter {@link IGRPUser} entities based on
     * the provided query parameters. Joins are used for navigating nested relationships in the entity model.
     *
     * @param query the query containing filter criteria
     * @return a JPA Specification object to be passed to the repository
     */
    private Specification<IGRPUser> buildSpecification(GetUsersQuery query) {
        Specification<IGRPUser> spec = Specification.where(null);

        if (query.getApplicationId() != null) {
            spec = spec.and((root, q, cb) -> {
                Join<Object, Object> roleJoin = root.join("roles", JoinType.INNER);
                Join<Object, Object> departmentJoin = roleJoin.join("department", JoinType.INNER);
                Join<Object, Object> applicationJoin = departmentJoin.join("applicationId", JoinType.INNER);
                return cb.equal(applicationJoin.get("id"), query.getApplicationId());
            });
        }

        if (query.getDepartmentId() != null) {
            spec = spec.and((root, q, cb) -> {
                Join<Object, Object> roleJoin = root.join("roles", JoinType.INNER);
                Join<Object, Object> departmentJoin = roleJoin.join("department", JoinType.INNER);
                return cb.equal(departmentJoin.get("id"), query.getDepartmentId());
            });
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