package cv.igrp.platform.access_management.global_configuration.application.queries;

import cv.igrp.platform.access_management.global_configuration.application.constants.GlobalConfigurationType;
import cv.igrp.platform.access_management.global_configuration.infrastructure.persistence.repository.GlobalConfigurationEntityRepository;
import cv.igrp.platform.access_management.global_configuration.mapper.GlobalConfigurationMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpErrorCode;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import cv.igrp.platform.access_management.global_configuration.application.dto.GlobalConfigurationDTO;

@Component
public class GetGlobalConfigurationQueryHandler implements QueryHandler<GetGlobalConfigurationQuery, ResponseEntity<GlobalConfigurationDTO>>{

  private static final Logger LOGGER = LoggerFactory.getLogger(GetGlobalConfigurationQueryHandler.class);

  private GlobalConfigurationEntityRepository repository;
  private GlobalConfigurationMapper mapper;

  public GetGlobalConfigurationQueryHandler(GlobalConfigurationEntityRepository repository, GlobalConfigurationMapper mapper) {
    this.repository = repository;
    this.mapper = mapper;
  }

  @IgrpQueryHandler
  @Transactional
  public ResponseEntity<GlobalConfigurationDTO> handle(GetGlobalConfigurationQuery query) {

    var globalConfigurationType = GlobalConfigurationType.fromCode(query.getType());

    if(globalConfigurationType.isEmpty())
      throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_GLOBAL_CONFIGURATION_TYPE_NOT_FOUND, query.getType());

    var configurations = repository.findByTypeOrderByLastModifiedDateDesc(GlobalConfigurationType.valueOf(query.getType()));

    if(configurations.isEmpty())
      throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_GLOBAL_CONFIGURATION_NOT_FOUND, query.getType());

    var configuration = configurations.getFirst();

    GlobalConfigurationDTO dto = mapper.toDto(configuration);

    return ResponseEntity.status(HttpStatus.OK) .body(dto);

  }

}