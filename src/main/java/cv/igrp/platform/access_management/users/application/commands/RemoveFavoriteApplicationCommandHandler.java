package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpErrorCode;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.FavoriteApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import cv.igrp.platform.access_management.shared.security.SubjectParser;


@Component
public class RemoveFavoriteApplicationCommandHandler implements CommandHandler<RemoveFavoriteApplicationCommand, ResponseEntity<String>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(RemoveFavoriteApplicationCommandHandler.class);

    private final AuthenticationHelper authenticationHelper;
    private final FavoriteApplicationEntityRepository favoriteApplicationEntityRepository;
    private final ApplicationEntityRepository applicationEntityRepository;
    private final IGRPUserEntityRepository userEntityRepository;

   public RemoveFavoriteApplicationCommandHandler(AuthenticationHelper authenticationHelper, FavoriteApplicationEntityRepository favoriteApplicationEntityRepository, ApplicationEntityRepository applicationEntityRepository, IGRPUserEntityRepository userEntityRepository) {
       this.authenticationHelper = authenticationHelper;
       this.favoriteApplicationEntityRepository = favoriteApplicationEntityRepository;
       this.applicationEntityRepository = applicationEntityRepository;
       this.userEntityRepository = userEntityRepository;
   }

   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<String> handle(RemoveFavoriteApplicationCommand command) {

       var currentUserSub = authenticationHelper.getSub();

       LOGGER.info("Removing application <{}> as favorite to user: {}",  command.getApplicationCode(), currentUserSub);

       var user = userEntityRepository.findByIdWithRolesAndPermissions(SubjectParser.parseUserSubjectOrThrow(currentUserSub)).orElseThrow(
               () -> IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_USER_NOT_FOUND_BY_EXTERNAL_ID, currentUserSub)
       );

       var application = applicationEntityRepository.findByCodeAndStatusNotDeleted(command.getApplicationCode());

       if(!favoriteApplicationEntityRepository.existsByUserAndApplication(user.getId(), application)) {
           throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_FAVORITE_APPLICATION_NOT_FAVORITED, command.getApplicationCode(), authenticationHelper.getSub());
       }

       var favoriteApplication = favoriteApplicationEntityRepository.findByUserId(user.getId()).orElseThrow(
               () -> IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_FAVORITE_APPLICATION_NONE_FOR_USER, user.getId())
       );

       favoriteApplication.getApplications().remove(application);

       favoriteApplicationEntityRepository.save(favoriteApplication);

       LOGGER.info("Application <{}> removed as favorite to user: {}", command.getApplicationCode(), currentUserSub);

       return ResponseEntity.noContent().build();

   }

}