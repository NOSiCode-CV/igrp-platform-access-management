package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.FavoriteApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GetFavoriteApplicationsQueryHandler implements QueryHandler<GetFavoriteApplicationsQuery, ResponseEntity<List<ApplicationDTO>>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetFavoriteApplicationsQueryHandler.class);

    private final FavoriteApplicationEntityRepository favoriteApplicationEntityRepository;
    private final IGRPUserEntityRepository userEntityRepository;
    private final ApplicationMapper applicationMapper;
    private final AuthenticationHelper authenticationHelper;

    public GetFavoriteApplicationsQueryHandler(FavoriteApplicationEntityRepository favoriteApplicationEntityRepository,
                                               IGRPUserEntityRepository userEntityRepository,
                                               ApplicationMapper applicationMapper,
                                               AuthenticationHelper authenticationHelper) {
        this.favoriteApplicationEntityRepository = favoriteApplicationEntityRepository;
        this.userEntityRepository = userEntityRepository;
        this.applicationMapper = applicationMapper;
        this.authenticationHelper = authenticationHelper;
    }

    @IgrpQueryHandler
    @Transactional
    public ResponseEntity<List<ApplicationDTO>> handle(GetFavoriteApplicationsQuery query) {

        var currentUserSub = authenticationHelper.getSub();

        LOGGER.info("Getting favorite applications for user: {}", currentUserSub);

        var user = userEntityRepository.findByExternalId(currentUserSub).orElseThrow(
                () -> IgrpResponseStatusException.of(HttpStatus.UNAUTHORIZED, "User with external ID " + currentUserSub + " not found")
        );

        var favoriteApps = favoriteApplicationEntityRepository.findByUserId(Integer.valueOf(user.getId())).orElseThrow(
                        () -> IgrpResponseStatusException.of(HttpStatus.NOT_FOUND, "Favorite applications for user with ID " + user.getId() + " not found")
        );

        var applicationDTOs = favoriteApps.getApplications()
                .stream()
                .filter(it -> query.getApplicationName() == null || it.getName().toLowerCase().contains(query.getApplicationName().toLowerCase()))
                .filter(it -> query.getApplicationCode() == null || it.getCode().toLowerCase().contains(query.getApplicationCode().toLowerCase()))
                .map(applicationMapper::toDto)
                .toList();

        return ResponseEntity.ok(applicationDTOs);

    }

}