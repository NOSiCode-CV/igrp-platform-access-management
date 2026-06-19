package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import org.springframework.transaction.annotation.Transactional;

/**
 * Command handler responsible for updating an existing {@link IGRPUserEntity} entity.
 * <p>
 * This handler processes {@link UpdateUserCommand} instances, which include the user ID and
 * an {@link IGRPUserDTO} containing the updated user data.
 * <p>
 * The handler performs the following:
 * <ul>
 *   <li>Retrieves the user entity by ID from the {@link IGRPUserEntityRepository}.</li>
 *   <li>Updates the user entity's fields only if the corresponding fields in the DTO are non-null.</li>
 *   <li>Manages role assignments in the IAM provider when user status changes</li>
 *   <li>Saves the updated entity using the repository.</li>
 *   <li>Maps the updated entity to a DTO and returns it in a {@link ResponseEntity}.</li>
 * </ul>
 *
 * <p>Defensive checks are applied to ensure existing data is not overwritten by {@code null} values.</p>
 *
 * @see UpdateUserCommand
 * @see IGRPUserDTO
 * @see IGRPUserEntityRepository
 * @see IGRPUserMapper
 */
@Component
public class UpdateUserCommandHandler implements CommandHandler<UpdateUserCommand, ResponseEntity<IGRPUserDTO>> {

    private static final Logger logger = LoggerFactory.getLogger(UpdateUserCommandHandler.class);

    private final IGRPUserEntityRepository userRepository;
    private final IGRPUserMapper userMapper;

    /**
     * Constructs the handler with required dependencies.
     *
     * @param userRepository the repository to retrieve and save user entities
     * @param userMapper     the mapper used to convert entities to DTOs
     */
    public UpdateUserCommandHandler(
            IGRPUserEntityRepository userRepository,
            IGRPUserMapper userMapper
    ) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    /**
     * Handles the update of an existing user.
     *
     * @param command the command containing the user ID and the updated user data
     * @return a {@link ResponseEntity} containing the updated {@link IGRPUserDTO}
     * @throws EntityNotFoundException if no user exists with the given ID
     */
    @IgrpCommandHandler
    @Transactional
    public ResponseEntity<IGRPUserDTO> handle(UpdateUserCommand command) {
        // PUT /api/users/{id} — the path variable is the user UUID, not the
        // username. Looking up by username here used to mis-route every update
        // attempt for OIDC-provisioned users (whose username is their email
        // address, not their UUID) into a spurious 404. Now we resolve by id,
        // which is what the route advertises and what the client sends.
        String id = command.getId();

        logger.info("Updating user with id={}", id);

        IGRPUserEntity user = userRepository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("User with id={} not found", id);
                    return IgrpResponseStatusException.of(
                            HttpStatus.NOT_FOUND,
                            "Invalid User",
                            "User not found with id: " + id);
                });

        IGRPUserDTO dto = command.getIgrpuserdto();

        // Update user fields
        if (dto.getName() != null) {
            user.setName(dto.getName());
        }

        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail());
        }

        if (dto.getPicture() != null) {
            user.setPicture(dto.getPicture());
        }

        if (dto.getSignature() != null) {
            user.setSignature(dto.getSignature());
        }

        if (dto.getNic() != null && !dto.getNic().isBlank()) {
            user.setNic(dto.getNic().toUpperCase());
        }

        if (dto.getPhoneNumber() != null && !dto.getPhoneNumber().isBlank()) {
            user.setPhoneNumber(dto.getPhoneNumber());
        }

        var updatedUser = userRepository.save(user);

        logger.info("User updated successfully: id={}, email={}", updatedUser.getId(), updatedUser.getEmail());

        return ResponseEntity.ok(userMapper.toDto(updatedUser));
    }

}