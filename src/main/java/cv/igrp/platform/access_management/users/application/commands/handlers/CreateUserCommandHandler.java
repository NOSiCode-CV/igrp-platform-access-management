package cv.igrp.platform.access_management.users.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;

import java.util.ArrayList;

import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.users.application.commands.commands.CreateUserCommand;

import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.IGRPUserRepository;

/**
 * Command handler responsible for creating a new {@link IGRPUser} entity in the system.
 * <p>
 * This handler receives a {@link CreateUserCommand} containing user creation data,
 * constructs a new {@link IGRPUser} instance, and persists it using {@link IGRPUserRepository}.
 * After persistence, the user is converted to a {@link IGRPUserDTO} using {@link IGRPUserMapper},
 * and returned in the HTTP response.
 * </p>
 *
 * <p><strong>Behavior:</strong></p>
 * <ul>
 *   <li>Initializes a user with an empty role list</li>
 *   <li>Does not assign roles at creation time</li>
 *   <li>Assumes that data validation is handled upstream (e.g., controller layer)</li>
 * </ul>
 *
 * @see CreateUserCommand
 * @see IGRPUserRepository
 * @see IGRPUserMapper
 * @see IGRPUserDTO
 */
@Service
public class CreateUserCommandHandler implements CommandHandler<CreateUserCommand, ResponseEntity<?>> {

    private final IGRPUserRepository userRepository;
    private final IGRPUserMapper userMapper;

    /**
     * Constructs the CreateUserCommandHandler with the required dependencies.
     *
     * @param userRepository the repository used to persist the user entity
     * @param userMapper the mapper used to convert between entities and DTOs
     */
    public CreateUserCommandHandler(IGRPUserRepository userRepository, IGRPUserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    /**
     * Handles the {@link CreateUserCommand} by creating a new user, saving it, and returning a response with the created user DTO.
     *
     * @param command the command containing user creation data
     * @return a {@link ResponseEntity} containing the created user as a {@link IGRPUserDTO}
     */
    @IgrpCommandHandler
    public ResponseEntity<IGRPUserDTO> handle(CreateUserCommand command) {
        IGRPUser user = new IGRPUser();
        user.setName(command.getIgrpuserdto().getName());
        user.setUsername(command.getIgrpuserdto().getUsername());
        user.setEmail(command.getIgrpuserdto().getEmail());
        user.setRoles(new ArrayList<>());

        var savedUser = userRepository.save(user);

        return ResponseEntity.ok(userMapper.toDto(savedUser));
    }
}






