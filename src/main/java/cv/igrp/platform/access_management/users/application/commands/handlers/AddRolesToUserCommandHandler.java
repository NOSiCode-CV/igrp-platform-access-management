package cv.igrp.platform.access_management.users.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.RoleRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.IGRPUserRepository;
import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.users.application.commands.commands.AddRolesToUserCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Command handler responsible for associating a {@link Role} with a specific {@link IGRPUser}.
 * <p>
 * This handler processes the {@link AddRolesToUserCommand} by:
 * <ul>
 *   <li>Fetching the user from the {@link IGRPUserRepository} using the provided user ID.</li>
 *   <li>Fetching the role from the {@link RoleRepository} using the provided role ID.</li>
 *   <li>Adding the user to the role's user set (initializing it if necessary).</li>
 *   <li>Saving the updated role and mapping it to a {@link RoleDTO}.</li>
 *   <li>Returning the mapped role in a {@link ResponseEntity} with HTTP status 201 (Created).</li>
 * </ul>
 *
 * @see AddRolesToUserCommand
 * @see IGRPUserRepository
 * @see RoleRepository
 * @see RoleMapper
 * @see RoleDTO
 */
@Service
public class AddRolesToUserCommandHandler implements CommandHandler<AddRolesToUserCommand, ResponseEntity<List<RoleDTO>>> {

    private static final Logger logger =
            LoggerFactory.getLogger(AddRolesToUserCommandHandler.class);

    private final IGRPUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RoleMapper roleMapper;

    /**
     * Constructs the handler with required dependencies.
     *
     * @param userRepository the repository used to retrieve user entities
     * @param roleRepository the repository used to retrieve and update roles
     * @param roleMapper the mapper used to convert role entities to DTOs
     */
    public AddRolesToUserCommandHandler(
            IGRPUserRepository userRepository,
            RoleRepository roleRepository,
            RoleMapper roleMapper) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.roleMapper = roleMapper;
    }

    /**
     * Handles the command to add a role to a user.
     *
     * @param command the command containing the user ID and RoleUserDTO to associate the corresponding ID
     * @return a {@link ResponseEntity} containing a list with the updated {@link RoleDTO}
     * @throws IgrpResponseStatusException if the user or role is not found
     */
    @IgrpCommandHandler
    public ResponseEntity<List<RoleDTO>> handle(AddRolesToUserCommand command) {
        Integer userId = command.getId();
        Integer roleId = command.getRoleuserdto().role_id();

        logger.info("Assigning role id={} to user id={}", roleId, userId);

        IGRPUser user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    logger.warn("User not found with id={}", userId);
                    return new IgrpResponseStatusException(
                            new IgrpProblem<>(HttpStatus.NOT_FOUND,"Invalid User id",
                                    "User not found with id: " + userId));
                });

        Role roleToAdd = roleRepository.findById(roleId)
                .orElseThrow(() -> {
                    logger.warn("Role not found with id={}", roleId);
                    return new IgrpResponseStatusException(
                            new IgrpProblem<>(HttpStatus.NOT_FOUND, "Invalid Role id",
                                    "Role not found with id: " + roleId));
                    });

        Set<IGRPUser> users = roleToAdd.getUsers();
        if (users == null) {
            users = new HashSet<>();
            roleToAdd.setUsers(users);
        }
        boolean isRoleAdded = users.add(user);

        if (isRoleAdded) {
            logger.info("User id={} successfully added to role id={}", userId, roleId);
        } else {
            logger.info("User id={} was already associated with role id={}", userId, roleId);
        }

        var roleUpdated = roleRepository.save(roleToAdd);
        RoleDTO roleDTO = roleMapper.mapToDto(roleUpdated);

        return ResponseEntity.status(HttpStatus.CREATED).body(List.of(roleDTO));
    }
}