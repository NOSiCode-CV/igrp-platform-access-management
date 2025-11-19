package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.security.ScopeContext;
import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import cv.igrp.platform.access_management.users.specs.UserSpecificationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;

@Component
public class GetUsersQueryHandler implements QueryHandler<GetUsersQuery, ResponseEntity<List<IGRPUserDTO>>>{

  private static final Logger LOGGER = LoggerFactory.getLogger(GetUsersQueryHandler.class);

  private final IGRPUserEntityRepository userRepository;
  private final IGRPUserMapper userMapper;
  private final UserSpecificationBuilder specification;

  public GetUsersQueryHandler(IGRPUserEntityRepository userRepository, IGRPUserMapper userMapper, UserSpecificationBuilder specification) {
    this.userRepository = userRepository;
    this.userMapper = userMapper;
    this.specification = specification;
  }

   @IgrpQueryHandler
  public ResponseEntity<List<IGRPUserDTO>> handle(GetUsersQuery query) {
     LOGGER.info("Handling GetUsersCommand: applicationCode={}, departmentCode={}, name={}, id={}, email={}",
             query.getApplicationCode(), query.getDepartmentCode(), query.getName(),
             query.getId(), query.getEmail());

     Specification<IGRPUserEntity> spec = specification.buildSpecification(query, new ScopeContext());
     List<IGRPUserDTO> users = userRepository.findAll(spec)
             .stream()
             .map(userMapper::toDto)
             .toList();

     return ResponseEntity.ok(users);
  }

}