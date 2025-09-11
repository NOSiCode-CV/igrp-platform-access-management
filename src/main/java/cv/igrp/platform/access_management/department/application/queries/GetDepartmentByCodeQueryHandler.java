package cv.igrp.platform.access_management.department.application.queries;

import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;



@Component
public class GetDepartmentByCodeQueryHandler implements QueryHandler<GetDepartmentByCodeQuery, ResponseEntity<DepartmentDTO>>{

    private static final Logger LOGGER = LoggerFactory.getLogger(GetDepartmentByCodeQueryHandler.class);

private final DepartmentEntityRepository departmentEntityRepository;
private final DepartmentMapper departmentMapper;

  public GetDepartmentByCodeQueryHandler(DepartmentEntityRepository departmentEntityRepository, DepartmentMapper departmentMapper) {
      this.departmentEntityRepository = departmentEntityRepository;
      this.departmentMapper = departmentMapper;
  }

   @IgrpQueryHandler
  public ResponseEntity<DepartmentDTO> handle(GetDepartmentByCodeQuery query) {
    String departmentCode = query.getCode();
    LOGGER.info("Fetching department with code={}", departmentCode);

     DepartmentEntity department = departmentEntityRepository.findByCodeAndStatusNot(departmentCode, DepartmentStatus.DELETED)
             .orElseThrow(() -> {
               LOGGER.warn("Department with code={} not found", departmentCode);
               return IgrpResponseStatusException.of(
                       HttpStatus.NOT_FOUND, "Invalide Department CODE", "Department not found with code: " + departmentCode);

             });
     DepartmentDTO dto = departmentMapper.toDto(department);
     LOGGER.info("Successfully retrieved department code={} name={}", dto.getCode(), dto.getName());

     return ResponseEntity.ok(dto);
  }

}