package cv.igrp.platform.access_management.users.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.users.application.queries.queries.GetCurrentUserQuery;
import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.IGRPUserRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;

/**
 * Handles the {@link GetCurrentUserQuery} by retrieving the currently authenticated user's information.
 * <p>
 * This handler uses the {@link AuthenticationHelper} to determine the current user's username,
 * looks up the user in the {@link IGRPUserRepository}, and maps the domain model to a {@link IGRPUserDTO}.
 * </p>
 *
 * <p>If the user is not found, a 404 response is returned.</p>
 *
 */
@Service
public class GetCurrentUserQueryHandler implements
        QueryHandler<GetCurrentUserQuery, ResponseEntity<IGRPUserDTO>>{

    private static final Logger logger =
            LoggerFactory.getLogger(GetCurrentUserQueryHandler.class);

    private final IGRPUserRepository igrpUserRepository;
   private final IGRPUserMapper userMapper;
   private final AuthenticationHelper authenticationHelper;

    /**
     * Constructs the handler with necessary dependencies.
     *
     * @param igrpUserRepository     the repository to retrieve user data
     * @param userMapper             the mapper to convert entities to DTOs
     * @param authenticationHelper   the helper to access the authenticated user context
     */
   public GetCurrentUserQueryHandler(
           IGRPUserRepository igrpUserRepository,
           IGRPUserMapper userMapper, AuthenticationHelper
                   authenticationHelper) {
      this.igrpUserRepository = igrpUserRepository;
      this.userMapper = userMapper;
      this.authenticationHelper = authenticationHelper;
   }

    /**
     * Handles the {@link GetCurrentUserQuery} by retrieving the current user based on the authentication context.
     *
     * @param query the query input (unused in this case)
     * @return a {@link ResponseEntity} containing {@link IGRPUserDTO} if found, or 404 otherwise
     */
   @IgrpQueryHandler
    public ResponseEntity<IGRPUserDTO> handle(GetCurrentUserQuery query) {
        String username = authenticationHelper.getPreferredUsername();

       logger.info("Fetching current user with username: {}", username);


       Optional<IGRPUser> optionalUser = igrpUserRepository.findByUsername(username);
        if (optionalUser.isEmpty()) {
            logger.warn("No user found with username: {}", username);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        IGRPUserDTO dto = userMapper.toDto(optionalUser.get());
       logger.info("User found. ID: {}, Username: {}", dto.getId(), dto.getUsername());

       return ResponseEntity.ok(dto);
    }
}