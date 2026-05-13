package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;

@Component
public class GetUserDepartmentsQueryHandler implements QueryHandler<GetUserDepartmentsQuery, ResponseEntity<List<DepartmentDTO>>>{

  private static final Logger LOGGER = LoggerFactory.getLogger(GetUserDepartmentsQueryHandler.class);

  private final DepartmentEntityRepository departmentRepository;
  private final IGRPUserEntityRepository userRepository;
  private final DepartmentMapper departmentMapper;

  public GetUserDepartmentsQueryHandler(
          DepartmentEntityRepository departmentRepository,
          IGRPUserEntityRepository userRepository,
          DepartmentMapper departmentMapper
  ) {
    this.departmentRepository = departmentRepository;
    this.userRepository = userRepository;
    this.departmentMapper = departmentMapper;
  }

   @IgrpQueryHandler
  public ResponseEntity<List<DepartmentDTO>> handle(GetUserDepartmentsQuery query) {

     var user = userRepository.findById(query.getId()).orElseThrow(
             () -> IgrpResponseStatusException.of(
                     HttpStatus.UNAUTHORIZED,
                     "User not found",
                     "User with ID: " + query.getId() + " not found in database."
             )
     );

     LOGGER.info("Getting departments for user: {}", user.getExternalId());

     List<DepartmentDTO> departments = departmentRepository.findByUserAndNotDeletedFiltered(user.getId(), query.getDepartmentCode())
             .stream()
             .map(departmentMapper::toDto)
             .toList();

     return ResponseEntity.ok(departments);

  }

}