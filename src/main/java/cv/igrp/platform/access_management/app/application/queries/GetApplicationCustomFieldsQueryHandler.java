package cv.igrp.platform.access_management.app.application.queries;

import cv.igrp.platform.access_management.shared.application.constants.CustomFieldTableName;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.CustomFieldEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.CustomFieldEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * Handles the retrieval of custom fields associated with a specific {@link ApplicationEntity}.
 *
 * <p>
 * This query handler is responsible for:
 * <ul>
 *     <li>Fetching a {@link CustomFieldEntity} entry from the {@link CustomFieldEntityRepository} using the application ID and table name.</li>
 *     <li>Throwing an {@link IgrpResponseStatusException} with HTTP 404 if the custom field is not found.</li>
 *     <li>Returning the custom fields map wrapped in a {@link ResponseEntity} with status {@link HttpStatus#OK}.</li>
 * </ul>
 *
 * @see cv.igrp.platform.access_management.app.application.queries.queries.GetApplicationCustomFieldsQuery
 * @see CustomFieldEntityRepository
 * @see CustomFieldTableName
 * @see IgrpResponseStatusException
 */
@Component
public class GetApplicationCustomFieldsQueryHandler implements QueryHandler<GetApplicationCustomFieldsQuery, ResponseEntity<Map<String, ?>>>{

  private CustomFieldEntityRepository customFieldRepository;

  /**
   * Constructs a new {@code GetApplicationCustomFieldsQueryHandler} with the required dependencies.
   *
   * @param customFieldRepository repository used to retrieve custom fields by table name and record ID
   */
  public GetApplicationCustomFieldsQueryHandler(CustomFieldEntityRepository customFieldRepository) {
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
    CustomFieldEntity customField = customFieldRepository
            .findByTableNameAndRecordId(CustomFieldTableName.APPLICATION.getName(), query.getId())
            .orElseThrow(() -> IgrpResponseStatusException.of(HttpStatus.NOT_FOUND, "CustomFieldEntity not found", "CustomFieldEntity not found for Application ID: " + query.getId()));
    return ResponseEntity.ok(customField.getFields());
  }

}