package cv.igrp.platform.access_management.users.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;

import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.users.application.queries.queries.GetCurrentUserQuery;
import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.IGRPUserRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;

@Service
public class GetCurrentUserQueryHandler implements QueryHandler<GetCurrentUserQuery, ResponseEntity<IGRPUserDTO>>{

   private final IGRPUserRepository igrpUserRepository;
   private final IGRPUserMapper userMapper;
   private final AuthenticationHelper authenticationHelper;

   public GetCurrentUserQueryHandler(IGRPUserRepository igrpUserRepository, IGRPUserMapper userMapper, AuthenticationHelper authenticationHelper) {
      this.igrpUserRepository = igrpUserRepository;
      this.userMapper = userMapper;
      this.authenticationHelper = authenticationHelper;
   }

   @IgrpQueryHandler
    public ResponseEntity<IGRPUserDTO> handle(GetCurrentUserQuery query) {

        String username = authenticationHelper.getPreferredUsername();

        Optional<IGRPUser> optionalUser = igrpUserRepository.findByUsername(username);
        if (optionalUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        IGRPUserDTO dto = userMapper.toDto(optionalUser.get());
        return ResponseEntity.ok(dto);
    }

}