package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.dto.UserMetadataDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class UpdateUserMetadataCommandHandler implements CommandHandler<UpdateUserMetadataCommand, ResponseEntity<UserMetadataDTO>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateUserMetadataCommandHandler.class);

    private final IGRPUserEntityRepository userRepository;

    public UpdateUserMetadataCommandHandler(IGRPUserEntityRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    @IgrpCommandHandler
    public ResponseEntity<UserMetadataDTO> handle(UpdateUserMetadataCommand command) {
        String id = command.getId();
        LOGGER.info("Updating metadata for user id={}", id);

        IGRPUserEntity user = userRepository.findById(id)
                .orElseThrow(() -> IgrpResponseStatusException.of(
                        HttpStatus.NOT_FOUND,
                        "Invalid User",
                        "User not found with ID: " + id));

        Map<String, Object> incoming = command.getMetadata() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(command.getMetadata());

        user.setMetadata(incoming);
        IGRPUserEntity saved = userRepository.save(user);

        return ResponseEntity.ok(UserMetadataDTO.builder()
                .userId(saved.getInternalId())
                .metadata(new LinkedHashMap<>(saved.getMetadata()))
                .build());
    }
}
