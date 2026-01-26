package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import cv.igrp.platform.access_management.shared.application.dto.RoleDepartmentDTO;

import java.util.Optional;

@Component
public class GetActiveUserRoleQueryHandler implements QueryHandler<GetActiveUserRoleQuery, ResponseEntity<RoleDepartmentDTO>> {

    private static final Logger logger = LoggerFactory.getLogger(GetActiveUserRoleQueryHandler.class);

    private final IGRPUserEntityRepository igrpUserRepository;

    /**
     * Constructs the handler with necessary dependencies.
     *
     * @param igrpUserRepository the repository to retrieve user data
     */
    public GetActiveUserRoleQueryHandler(
            IGRPUserEntityRepository igrpUserRepository
    ) {
        this.igrpUserRepository = igrpUserRepository;
    }

    @IgrpQueryHandler
    public ResponseEntity<RoleDepartmentDTO> handle(GetActiveUserRoleQuery query) {

        Integer userId = query.getId();

        logger.info("Fetching user active role with ID: {}", userId);

        Optional<IGRPUserEntity> optionalUser = igrpUserRepository.findById(userId);
        if (optionalUser.isEmpty()) {
            logger.warn("No user found with ID: {}", userId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        IGRPUserEntity user = optionalUser.get();

        if (user.getActiveRole() == null) {
            logger.warn("User with ID: {} has no active roles", userId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        RoleDepartmentDTO dto = new RoleDepartmentDTO(user.getActiveRole().getCode(), user.getActiveRole().getDepartment().getCode());
        logger.info("User ID : {} has active role: {} (Department: {})",  userId, user.getActiveRole().getCode(), user.getActiveRole().getDepartment().getCode());

        return ResponseEntity.ok(dto);

    }

}