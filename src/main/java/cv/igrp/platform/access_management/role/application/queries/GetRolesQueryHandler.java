package cv.igrp.platform.access_management.role.application.queries;

import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import lombok.extern.slf4j.Slf4j;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import java.util.List;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import org.springframework.transaction.annotation.Transactional;

import static cv.igrp.platform.access_management.shared.infrastructure.service.ConfigurationService.SUPER_ADMIN_ROLE;

/**
 * Query handler responsible for retrieving all active or inactive {@link RoleDTO} entries.
 *
 * <p>This handler performs the following:</p>
 * <ul>
 *   <li>Retrieves all {@link RoleEntity} entities from the database that have a status of {@link Status#ACTIVE} or {@link Status#INACTIVE}</li>
 *   <li>Maps each entity to its {@link RoleDTO} representation using {@link RoleMapper}</li>
 *   <li>Returns the results wrapped in a {@link ResponseEntity} with HTTP status {@link HttpStatus#OK}</li>
 * </ul>
 *
 * @see GetRolesQuery
 * @see RoleEntityRepository
 * @see RoleMapper
 * @see RoleDTO
 * @see Status
 */
@Slf4j
@Component
public class GetRolesQueryHandler implements QueryHandler<GetRolesQuery, ResponseEntity<List<RoleDTO>>>{

  private final RoleEntityRepository roleRepository;
  private final RoleMapper roleMapper;

  /**
   * Constructs a new instance of {@code GetRolesQueryHandler} with the required dependencies.
   *
   * @param roleRepository the repository used to fetch roles from the database
   * @param roleMapper     the mapper used to convert {@link RoleEntity} entities to {@link RoleDTO}
   */
  public GetRolesQueryHandler(RoleEntityRepository roleRepository, RoleMapper roleMapper) {

    this.roleRepository = roleRepository;
    this.roleMapper = roleMapper;
  }

  /**
   * Handles the {@link GetRolesQuery} to retrieve all active or inactive roles.
   *
   * <ul>
   *     <li>Queries the repository for roles with {@link Status#ACTIVE} or {@link Status#INACTIVE}</li>
   *     <li>Maps each {@link RoleEntity} entity to a {@link RoleDTO}</li>
   *     <li>Returns the list wrapped in a {@link ResponseEntity} with status 200 (OK)</li>
   * </ul>
   *
   * @param query the query object (currently unused, but may include filters in the future)
   * @return a {@link ResponseEntity} containing the list of {@link RoleDTO}
   */
  @IgrpQueryHandler
  @Transactional(readOnly = true)
  public ResponseEntity<List<RoleDTO>> handle(GetRolesQuery query) {
    log.info("Get Roles with filters - departmentCode: {}, code: {}", query.getDepartmentCode(), query.getCode());
    Specification<RoleEntity> specs = buildSpecification(query.getDepartmentCode(), query.getCode());
    List<RoleEntity> allRoles = roleRepository.findAll(specs);
    List<RoleDTO> collectedRole = allRoles.stream()
            .map(roleMapper::mapToDto)
            .toList();
    return new ResponseEntity<>(collectedRole, HttpStatus.OK);
  }

  /**
   * Builds a dynamic JPA {@link Specification} based on optional code and code filters.
   *
   * @param departmentCode the exact department code to match (optional)
   * @param code the code to search for (optional)
   * @return a {@link Specification} representing the composed query filters
   */
  private Specification<RoleEntity> buildSpecification(String departmentCode, String code) {

    Specification<RoleEntity> specs = Specification.allOf();

    if(departmentCode != null && !departmentCode.isBlank()) {
        specs = specs.and((root, _, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("department").get("code"), departmentCode)
        );
    }

    if(code != null && !code.isBlank()) {
        specs = specs.and((root, _, criteriaBuilder) ->
                criteriaBuilder.like(criteriaBuilder.lower(root.get("code")), "%" + code.toLowerCase() + "%")
        );
    }

    specs = specs.and((root, _, _) ->
            root.get("status").in(Status.ACTIVE, Status.INACTIVE)
    );

    specs = specs.and((root, _, criteriaBuilder) ->
            criteriaBuilder.notEqual(root.get("code"), SUPER_ADMIN_ROLE)
    );

    return specs;

  }

}