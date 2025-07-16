package cv.igrp.platform.access_management.resource.application.queries;

import cv.igrp.platform.access_management.shared.application.constants.CustomFieldTableName;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
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
import java.util.Optional;

/**
 * Query handler that retrieves custom fields associated with a specific ID.
 * <p>
 * This handler processes {@link GetResourceCustomFieldsQuery} queries by using the provided
 *  ID to fetch custom field metadata from the {@link CustomFieldEntityRepository}.
 * If the record is found, the custom fields are returned as a key-value map. If not,
 * a {@link IgrpResponseStatusException} is thrown with a 404 NOT FOUND status.
 * </p>
 *
 * @see GetResourceCustomFieldsQuery
 * @see CustomFieldEntity
 * @see CustomFieldEntityRepository
 * @see IgrpResponseStatusException
 */
@Component
public class GetResourceCustomFieldsQueryHandler implements QueryHandler<GetResourceCustomFieldsQuery, ResponseEntity<Map<String, ?>>>{

  private static final Logger logger = LoggerFactory.getLogger(GetResourceCustomFieldsQueryHandler.class);

  private final CustomFieldEntityRepository customFieldRepository;

  /**
   * Constructs the {@code GetResourceCustomFieldsQueryHandler} with the required dependency.
   *
   * @param customFieldRepository the repository used to fetch {@link CustomFieldEntity}
   *                              entries based on table name and record ID
   */
  public GetResourceCustomFieldsQueryHandler(
          CustomFieldEntityRepository customFieldRepository) {
    this.customFieldRepository = customFieldRepository;
  }

  /**
   * Handles a {@link cv.igrp.platform.access_management.resource.application.queries.queries.GetResourceCustomFieldsQuery} request by retrieving the custom fields
   * for a given resource ID.
   *
   * @param query the query object containing the resource ID
   * @return a {@link ResponseEntity} containing the custom fields as a map of keys and values,
   *         or an empty map if no fields exist
   * @throws IgrpResponseStatusException if no {@link CustomFieldEntity} entry exists for the given ID
   */
  @IgrpQueryHandler
  public ResponseEntity<Map<String, ?>> handle(GetResourceCustomFieldsQuery query) {
    Integer resourceId = query.getId();

    logger.info("Fetching custom fields for resource ID: {}", resourceId);

    CustomFieldEntity customField = customFieldRepository
            .findByTableNameAndRecordId(CustomFieldTableName.RESOURCE.getName(), query.getId())
            .orElseThrow(() -> {
              logger.warn("No custom field found for resource ID: {}", resourceId);
              return IgrpResponseStatusException.of(
                      HttpStatus.NOT_FOUND,
                      "CustomFieldEntity not found",
                      "CustomFieldEntity not found for Resource ID: " + resourceId);
            });

    logger.info("Custom fields successfully retrieved for resource ID: {}", resourceId);
    return ResponseEntity.ok(Optional.ofNullable(customField.getFields()).orElse(Map.of()));
  }

}