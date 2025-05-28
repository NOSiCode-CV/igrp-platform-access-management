package cv.igrp.platform.access_management.global_configuration.application.queries.handlers;
import cv.igrp.platform.access_management.global_configuration.application.constants.GlobalConfigurationType;
import cv.igrp.platform.access_management.global_configuration.application.dto.GlobalConfigurationDTO;
import cv.igrp.platform.access_management.global_configuration.infrastructure.persistence.GlobalConfigurationRepository;
import cv.igrp.platform.access_management.global_configuration.mapper.GlobalConfigurationMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.global_configuration.application.queries.queries.GetGlobalConfigurationQuery;


@Service
public class GetGlobalConfigurationQueryHandler implements QueryHandler<GetGlobalConfigurationQuery, ResponseEntity<GlobalConfigurationDTO>>{

  private static final Logger LOGGER = LoggerFactory.getLogger(GetGlobalConfigurationQueryHandler.class);

private GlobalConfigurationRepository repository;
private GlobalConfigurationMapper mapper;

   public GetGlobalConfigurationQueryHandler(GlobalConfigurationRepository repository, GlobalConfigurationMapper mapper) {
       this.repository = repository;
       this.mapper = mapper;
   }

   @IgrpQueryHandler
   public ResponseEntity<GlobalConfigurationDTO> handle(GetGlobalConfigurationQuery query) {

       var globalConfigurationType = GlobalConfigurationType.fromCode(query.getType());

       if(globalConfigurationType.isEmpty())
           throw new IgrpResponseStatusException(new IgrpProblem<>(HttpStatus.BAD_REQUEST, "Type not found","Global Configuration type: " +  query.getType() + " not found" ));

       var configurations = repository.findByTypeOrderByLastModifiedDateDesc(GlobalConfigurationType.valueOf(query.getType()));

       if(configurations.isEmpty())
           throw new IgrpResponseStatusException(new IgrpProblem<>(HttpStatus.NOT_FOUND, "Global Configuration not found","Global Configuration with type: " +  query.getType() + " not found" ));

       var configuration = configurations.getFirst();

       GlobalConfigurationDTO dto = mapper.toDto(configuration);

       return ResponseEntity.status(HttpStatus.OK) .body(dto);


   }

}