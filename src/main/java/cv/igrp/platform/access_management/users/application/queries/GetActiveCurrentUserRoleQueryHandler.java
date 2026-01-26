package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
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
public class GetActiveCurrentUserRoleQueryHandler implements QueryHandler<GetActiveCurrentUserRoleQuery, ResponseEntity<RoleDepartmentDTO>> {

    private static final Logger logger = LoggerFactory.getLogger(GetActiveCurrentUserRoleQueryHandler.class);

    private final IGRPUserEntityRepository igrpUserRepository;
    private final AuthenticationHelper authenticationHelper;

    /**
     * Constructs the handler with necessary dependencies.
     *
     * @param igrpUserRepository     the repository to retrieve user data
     * @param authenticationHelper   the helper to access the authenticated user context
     */
    public GetActiveCurrentUserRoleQueryHandler(
            IGRPUserEntityRepository igrpUserRepository,
            AuthenticationHelper authenticationHelper
    ) {
        this.igrpUserRepository = igrpUserRepository;
        this.authenticationHelper = authenticationHelper;
    }

    @IgrpQueryHandler
    public ResponseEntity<RoleDepartmentDTO> handle(GetActiveCurrentUserRoleQuery query) {

        String externalId = authenticationHelper.getSub();

        logger.info("Fetching current user active role with sub: {}", externalId);

        Optional<IGRPUserEntity> optionalUser = igrpUserRepository.findByExternalId(externalId);
        if (optionalUser.isEmpty()) {
            logger.warn("No user found with sub: {}", externalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        IGRPUserEntity user = optionalUser.get();

        if (user.getActiveRole() == null) {
            logger.warn("User with sub: {} has no active roles", externalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        RoleDepartmentDTO dto = new RoleDepartmentDTO(user.getActiveRole().getCode(), user.getActiveRole().getDepartment().getCode());
        logger.info("User ID : {} has active role: {} (Department: {})",  externalId, user.getActiveRole().getCode(),  user.getActiveRole().getDepartment().getCode());

        return ResponseEntity.ok(dto);

    }

}