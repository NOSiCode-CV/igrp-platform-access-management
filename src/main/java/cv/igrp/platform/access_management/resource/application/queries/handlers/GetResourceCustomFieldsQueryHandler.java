package cv.igrp.platform.access_management.resource.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.shared.application.constants.CustomFieldTableName;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.CustomField;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.CustomFieldRepository;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.resource.application.queries.queries.GetResourceCustomFieldsQuery;
import java.util.Map;

@Service
public class GetResourceCustomFieldsQueryHandler implements QueryHandler<GetResourceCustomFieldsQuery, ResponseEntity<Map<String, ?>>>{

   private CustomFieldRepository customFieldRepository;

   public GetResourceCustomFieldsQueryHandler(CustomFieldRepository customFieldRepository) {
      this.customFieldRepository = customFieldRepository;
   }

   @IgrpQueryHandler
   public ResponseEntity<Map<String, ?>> handle(GetResourceCustomFieldsQuery query) {
      CustomField customField = customFieldRepository
              .findByTableNameAndRecordId(CustomFieldTableName.RESOURCE.getName(), query.getId())
              .orElseThrow(() -> {
                 return new IgrpResponseStatusException(new IgrpProblem<String>(HttpStatus.NOT_FOUND, "CustomField not found", "CustomField not found for Resource ID: " + query.getId()));
              });
      return ResponseEntity.ok(customField.getFields());
   }

}