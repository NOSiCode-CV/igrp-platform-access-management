package cv.igrp.platform.access_management.global_configuration.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.global_configuration.domain.models.GlobalConfiguration;
import cv.igrp.platform.access_management.global_configuration.infrastructure.persistence.GlobalConfigurationRepository;
import cv.igrp.platform.access_management.global_configuration.mapper.GlobalConfigurationMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.platform.access_management.global_configuration.application.commands.commands.SetGlobalConfigurationCommand;

import cv.igrp.platform.access_management.global_configuration.application.dto.GlobalConfigurationDTO;

@Service
public class SetGlobalConfigurationCommandHandler implements CommandHandler<SetGlobalConfigurationCommand, ResponseEntity<GlobalConfigurationDTO>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(SetGlobalConfigurationCommandHandler.class);

   private GlobalConfigurationRepository repository;
   private GlobalConfigurationMapper mapper;

   public SetGlobalConfigurationCommandHandler(GlobalConfigurationRepository repository, GlobalConfigurationMapper mapper) {
      this.repository = repository;
      this.mapper = mapper;
   }

   @IgrpCommandHandler
   public ResponseEntity<GlobalConfigurationDTO> handle(SetGlobalConfigurationCommand command) {

      GlobalConfiguration globalConfiguration = mapper.toEntity(command.getGlobalconfiguration());

      GlobalConfiguration saveGlobalConfiguration = repository.save(globalConfiguration);

      GlobalConfigurationDTO dto = mapper.toDto(saveGlobalConfiguration);

      return ResponseEntity.status(HttpStatus.OK).body(dto);

   }

}