package cv.igrp.platform.access_management.users.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.IGRPUserRepository;
import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.users.application.queries.queries.GetUserQuery;
import java.util.List;

@Service
public class GetUserQueryHandler implements QueryHandler<GetUserQuery, ResponseEntity<IGRPUserDTO>>{

   private final IGRPUserRepository igrpUserRepository;
   private final IGRPUserMapper igrpUserMapper;

   public GetUserQueryHandler(IGRPUserRepository igrpUserRepository, IGRPUserMapper igrpUserMapper) {
      this.igrpUserRepository = igrpUserRepository;
      this.igrpUserMapper = igrpUserMapper;
   }

   @IgrpQueryHandler
   public ResponseEntity<IGRPUserDTO> handle(GetUserQuery query) {

      if (query.getId() != null) {
         return ResponseEntity.ok(igrpUserMapper.toDto(igrpUserRepository.findById(query.getId())
                 .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + query.getId()))));
      }

      return ResponseEntity.badRequest().body(null);

   }

}