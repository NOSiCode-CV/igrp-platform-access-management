package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class GetUserMetadataQueryHandler implements QueryHandler<GetUserMetadataQuery, ResponseEntity<UserMetadataDTO>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetUserMetadataQueryHandler.class);

    private final IGRPUserEntityRepository userRepository;

    public GetUserMetadataQueryHandler(IGRPUserEntityRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    @IgrpQueryHandler
    public ResponseEntity<UserMetadataDTO> handle(GetUserMetadataQuery query) {
        Integer id = query.getId();
        LOGGER.debug("Fetching metadata for user id={}", id);

        IGRPUserEntity user = userRepository.findById(id)
                .orElseThrow(() -> IgrpResponseStatusException.of(
                        HttpStatus.NOT_FOUND,
                        "Invalid User",
                        "User not found with ID: " + id));

        Map<String, Object> metadata = user.getMetadata() == null
                ? Collections.emptyMap()
                : new LinkedHashMap<>(user.getMetadata());

        return ResponseEntity.ok(UserMetadataDTO.builder()
                .userId(user.getInternalId())
                .metadata(metadata)
                .build());
    }
}
