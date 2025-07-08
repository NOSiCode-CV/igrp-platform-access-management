package cv.igrp.platform.access_management.app.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.app.application.queries.queries.GetApplicationCustomFieldsQuery;
import cv.igrp.platform.access_management.shared.application.constants.CustomFieldTableName;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.domain.models.CustomField;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.CustomFieldRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.util.Map;

/**
 * Handles the retrieval of custom fields associated with a specific {@link Application}.
 *
 * <p>
 * This query handler is responsible for:
 * <ul>
 *     <li>Fetching a {@link CustomField} entry from the {@link CustomFieldRepository} using the application ID and table name.</li>
 *     <li>Throwing an {@link IgrpResponseStatusException} with HTTP 404 if the custom field is not found.</li>
 *     <li>Returning the custom fields map wrapped in a {@link ResponseEntity} with status {@link HttpStatus#OK}.</li>
 * </ul>
 *
 * @see GetApplicationCustomFieldsQuery
 * @see CustomFieldRepository
 * @see CustomFieldTableName
 * @see IgrpResponseStatusException
 */
@Service
public class GetApplicationCustomFieldsQueryHandler implements QueryHandler<GetApplicationCustomFieldsQuery, ResponseEntity<Map<String, ?>>>{

   private CustomFieldRepository customFieldRepository;

   /**
    * Constructs a new {@code GetApplicationCustomFieldsQueryHandler} with the required dependencies.
    *
    * @param customFieldRepository repository used to retrieve custom fields by table name and record ID
    */
   public GetApplicationCustomFieldsQueryHandler(CustomFieldRepository customFieldRepository) {
      this.customFieldRepository = customFieldRepository;
   }

   /**
    * Handles the query to fetch custom fields for a given application ID.
    *
    * @param query the query containing the application ID
    * @return a {@link ResponseEntity} containing the map of custom fields
    * @throws IgrpResponseStatusException if no custom field entry is found for the given application ID
    */
   @IgrpQueryHandler
   public ResponseEntity<Map<String, ?>> handle(GetApplicationCustomFieldsQuery query) {
      CustomField customField = customFieldRepository
              .findByTableNameAndRecordId(CustomFieldTableName.APPLICATION.getName(), query.getId())
              .orElseThrow(() -> IgrpResponseStatusException.of(HttpStatus.NOT_FOUND, "CustomField not found", "CustomField not found for Application ID: " + query.getId()));
      return ResponseEntity.ok(customField.getFields());
   }

}