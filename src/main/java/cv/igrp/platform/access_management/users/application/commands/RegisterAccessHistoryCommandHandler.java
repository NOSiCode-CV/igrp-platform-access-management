package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.AccessHistoryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.AccessHistoryEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import cv.igrp.platform.access_management.shared.security.SubjectParser;


@Component
public class RegisterAccessHistoryCommandHandler implements CommandHandler<RegisterAccessHistoryCommand, ResponseEntity<String>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(RegisterAccessHistoryCommandHandler.class);

   private final AccessHistoryEntityRepository accessHistoryRepository;
   private final ApplicationEntityRepository applicationEntityRepository;
   private final IGRPUserEntityRepository userEntityRepository;
   private final AuthenticationHelper authenticationHelper;

   public RegisterAccessHistoryCommandHandler(AccessHistoryEntityRepository accessHistoryRepository, ApplicationEntityRepository applicationEntityRepository, IGRPUserEntityRepository userEntityRepository, AuthenticationHelper authenticationHelper) {
      this.accessHistoryRepository = accessHistoryRepository;
      this.applicationEntityRepository = applicationEntityRepository;
      this.userEntityRepository = userEntityRepository;
      this.authenticationHelper = authenticationHelper;
   }

   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<String> handle(RegisterAccessHistoryCommand command) {

      var currentUserSub = authenticationHelper.getSub();

      LOGGER.info("Registering access history on application <{}> for user with external ID: {}", command.getApplicationCode(), currentUserSub);

      var user = userEntityRepository.findByIdWithRolesAndPermissions(SubjectParser.parseUserSubjectOrThrow(currentUserSub)).orElseThrow(
              () -> IgrpResponseStatusException.of(HttpStatus.UNAUTHORIZED, "User with external ID " + currentUserSub + " not found")
      );

      var application = applicationEntityRepository.findByCodeAndStatusNotDeleted(command.getApplicationCode());

      var accessHistoryOpt = accessHistoryRepository.findByUserIdAndApplication(user.getId(), application);

      if(accessHistoryOpt.isEmpty()) {

         var newAccessHistory = new AccessHistoryEntity();

         newAccessHistory.setLastAccess(LocalDateTime.now());
         newAccessHistory.setUserId(user.getId());
         newAccessHistory.setApplication(application);

         accessHistoryRepository.save(newAccessHistory);

      } else {

         var accessHistory = accessHistoryOpt.get();
         accessHistory.setLastAccess(LocalDateTime.now());
         accessHistoryRepository.save(accessHistory);

      }

      LOGGER.info("Access history registered successfully on application <{}> for user with external ID: {}", command.getApplicationCode(), currentUserSub);

      return ResponseEntity.noContent().build();

   }

}