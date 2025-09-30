package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.framework.auth.core.exception.IAMException;
import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

/**
 * Command handler responsible for creating a new {@link IGRPUserEntity} entity in the system.
 * <p>
 * This handler receives a {@link CreateUserCommand} containing user creation data,
 * constructs a new {@link IGRPUserEntity} instance, and persists it using {@link IGRPUserEntityRepository}.
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
 * @see IGRPUserEntityRepository
 * @see IGRPUserMapper
 * @see IGRPUserDTO
 */
@Component
public class CreateUserCommandHandler implements CommandHandler<CreateUserCommand, ResponseEntity<IGRPUserDTO>> {

   private static final Logger logger = LoggerFactory.getLogger(CreateUserCommandHandler.class);

   private final IGRPUserEntityRepository userRepository;
   private final IGRPUserMapper userMapper;
   private final IAdapter adapter;

   /**
    * Constructs the CreateUserCommandHandler with the required dependencies.
    *
    * @param userRepository the repository used to persist the user entity
    * @param userMapper the mapper used to convert between entities and DTOs
    */
   public CreateUserCommandHandler(
           IGRPUserEntityRepository userRepository,
           IGRPUserMapper userMapper, IAdapter adapter) {
      this.userRepository = userRepository;
      this.userMapper = userMapper;
      this.adapter = adapter;
   }

   /**
    * Handles the {@link CreateUserCommand} by creating a new user, saving it, and returning a response with the created user DTO.
    *
    * @param command the command containing user creation data
    * @return a {@link ResponseEntity} containing the created user as a {@link IGRPUserDTO}
    */
   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<IGRPUserDTO> handle(CreateUserCommand command) {
      var dto = command.getIgrpuserdto();

      logger.info("Creating new user: username={}, email={}", dto.getUsername(), dto.getEmail());

      // Verify if username exists
      if (userRepository.existsByUsername(dto.getUsername()))
         throw IgrpResponseStatusException.of(
                 HttpStatus.CONFLICT,
                 "User with username %s exists already".formatted(dto.getUsername())
         );

      var providerUser = adapter.resolveUser(dto.getUsername());

      if(providerUser.isPresent()) {
         IGRPUserEntity user = new IGRPUserEntity();
         user.setName(dto.getName());
         user.setUsername(dto.getUsername());
         user.setEmail(dto.getEmail());
         user.setPicture(dto.getPicture());
         user.setSignature(dto.getSignature());
         user.setRoles(new ArrayList<>());
         var savedUser = userRepository.save(user);
         logger.info("User created successfully with id={}", savedUser.getId());
         return ResponseEntity.ok(userMapper.toDto(savedUser));
      } else {
         throw IgrpResponseStatusException.of(
                 HttpStatus.BAD_REQUEST,
                 "User Creation Failed",
                 "The specified user does not exist in the Identity Provider."
         );
      }

   }

}