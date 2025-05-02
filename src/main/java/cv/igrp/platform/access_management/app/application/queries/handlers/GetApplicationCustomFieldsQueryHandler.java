package cv.igrp.platform.access_management.app.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.app.application.queries.queries.GetApplicationCustomFieldsQuery;
import cv.igrp.platform.access_management.shared.application.constants.CustomFieldTableName;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.CustomField;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.CustomFieldRepository;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class GetApplicationCustomFieldsQueryHandler implements QueryHandler<GetApplicationCustomFieldsQuery, ResponseEntity<Map<String, ?>>>{

   private CustomFieldRepository customFieldRepository;

   public GetApplicationCustomFieldsQueryHandler(CustomFieldRepository customFieldRepository) {
      this.customFieldRepository = customFieldRepository;
   }

   @IgrpQueryHandler
   public ResponseEntity<Map<String, ?>> handle(GetApplicationCustomFieldsQuery query) {
      CustomField customField = customFieldRepository
              .findByTableNameAndRecordId(CustomFieldTableName.APPLICATION.getName(), query.getId())
              .orElseThrow(() -> {
                 return new IgrpResponseStatusException(new IgrpProblem<String>(HttpStatus.NOT_FOUND, "CustomField not found", "CustomField not found for Application ID: " + query.getId()));
              });
      return ResponseEntity.ok(customField.getFields());
   }

}