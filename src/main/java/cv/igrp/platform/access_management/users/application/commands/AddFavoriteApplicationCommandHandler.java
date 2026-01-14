package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.FavoriteApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.FavoriteApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;


@Component
public class AddFavoriteApplicationCommandHandler implements CommandHandler<AddFavoriteApplicationCommand, ResponseEntity<String>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AddFavoriteApplicationCommandHandler.class);

    private final AuthenticationHelper authenticationHelper;
    private final FavoriteApplicationEntityRepository favoriteApplicationEntityRepository;
    private final ApplicationEntityRepository applicationEntityRepository;
    private final IGRPUserEntityRepository userEntityRepository;


    public AddFavoriteApplicationCommandHandler(AuthenticationHelper authenticationHelper, FavoriteApplicationEntityRepository favoriteApplicationEntityRepository, ApplicationEntityRepository applicationEntityRepository, IGRPUserEntityRepository userEntityRepository) {
        this.authenticationHelper = authenticationHelper;
        this.favoriteApplicationEntityRepository = favoriteApplicationEntityRepository;
        this.applicationEntityRepository = applicationEntityRepository;
        this.userEntityRepository = userEntityRepository;
    }

    @IgrpCommandHandler
    @Transactional
    public ResponseEntity<String> handle(AddFavoriteApplicationCommand command) {

        var currentUserSub = authenticationHelper.getSub();

        LOGGER.info("Adding application <{}> as favorite to user: {}",  command.getApplicationCode(), currentUserSub);

        var user = userEntityRepository.findByExternalId(currentUserSub).orElseThrow(
                () -> IgrpResponseStatusException.of(HttpStatus.UNAUTHORIZED, "User with external ID " + currentUserSub + " not found")
        );

        var application = applicationEntityRepository.findByCodeAndStatusNotDeleted(command.getApplicationCode());

        if(favoriteApplicationEntityRepository.existsByUserAndApplication(Integer.valueOf(user.getId()), application)) {
            throw IgrpResponseStatusException.of(HttpStatus.BAD_REQUEST, "Application <" + command.getApplicationCode() + "> is already a favorite of user: " + authenticationHelper.getSub());
        }

        var favoriteApplicationOpt = favoriteApplicationEntityRepository.findByUserId(Integer.valueOf(user.getId()));

        var favoriteApplication = favoriteApplicationOpt.orElseGet(FavoriteApplicationEntity::new);

        if(favoriteApplicationOpt.isEmpty()) favoriteApplication.setUserId(Integer.valueOf(user.getId()));

        favoriteApplication.getApplications().add(application);

        favoriteApplicationEntityRepository.save(favoriteApplication);

        LOGGER.info("Application <{}> added as favorite to user: {}", command.getApplicationCode(), currentUserSub);

        return ResponseEntity.noContent().build();

    }

}