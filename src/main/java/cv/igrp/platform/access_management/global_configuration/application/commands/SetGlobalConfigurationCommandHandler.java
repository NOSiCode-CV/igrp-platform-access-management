package cv.igrp.platform.access_management.global_configuration.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.global_configuration.infrastructure.persistence.entity.GlobalConfigurationEntity;
import cv.igrp.platform.access_management.global_configuration.infrastructure.persistence.repository.GlobalConfigurationEntityRepository;
import cv.igrp.platform.access_management.global_configuration.mapper.GlobalConfigurationMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cv.igrp.platform.access_management.global_configuration.application.dto.GlobalConfigurationDTO;

@Component
public class SetGlobalConfigurationCommandHandler implements CommandHandler<SetGlobalConfigurationCommand, ResponseEntity<GlobalConfigurationDTO>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(SetGlobalConfigurationCommandHandler.class);

   private GlobalConfigurationEntityRepository repository;
   private GlobalConfigurationMapper mapper;

   public SetGlobalConfigurationCommandHandler(GlobalConfigurationEntityRepository repository, GlobalConfigurationMapper mapper) {
      this.repository = repository;
      this.mapper = mapper;
   }

   @IgrpCommandHandler
   public ResponseEntity<GlobalConfigurationDTO> handle(cv.igrp.platform.access_management.global_configuration.application.commands.commands.SetGlobalConfigurationCommand command) {

      GlobalConfigurationEntity globalConfiguration = mapper.toEntity(command.getGlobalconfiguration());

      GlobalConfigurationEntity saveGlobalConfiguration = repository.save(globalConfiguration);

      GlobalConfigurationDTO dto = mapper.toDto(saveGlobalConfiguration);

      return ResponseEntity.status(HttpStatus.OK).body(dto);

   }

}