package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpErrorCode;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.FavoriteApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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

        var user = userEntityRepository.findByExternalIdWithRolesAndPermissions(currentUserSub).orElseThrow(
                () -> IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_USER_NOT_FOUND_BY_EXTERNAL_ID, currentUserSub)
        );

        var favoriteApps = favoriteApplicationEntityRepository.findByUserId(Integer.valueOf(user.getId())).orElse(null);

        if(favoriteApps == null) return ResponseEntity.ok(new ArrayList<>());

        var applicationDTOs = favoriteApps.getApplications()
                .stream()
                .filter(it -> query.getApplicationName() == null || it.getName().toLowerCase().contains(query.getApplicationName().toLowerCase()))
                .filter(it -> query.getApplicationCode() == null || it.getCode().toLowerCase().contains(query.getApplicationCode().toLowerCase()))
                .map(applicationMapper::toDto)
                .toList();

        return ResponseEntity.ok(applicationDTOs);

    }

}