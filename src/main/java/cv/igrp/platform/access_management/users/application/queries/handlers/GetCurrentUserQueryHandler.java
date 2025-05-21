package cv.igrp.platform.access_management.users.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;

import java.util.Optional;

import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.users.application.queries.queries.GetCurrentUserQuery;
import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.IGRPUserRepository;

@Service
public class GetCurrentUserQueryHandler implements QueryHandler<GetCurrentUserQuery, ResponseEntity<IGRPUserDTO>>{

   private final IGRPUserRepository igrpUserRepository;
   private final IGRPUserMapper userMapper;

   public GetCurrentUserQueryHandler(IGRPUserRepository igrpUserRepository, IGRPUserMapper userMapper) {
      this.igrpUserRepository = igrpUserRepository;
      this.userMapper = userMapper;
   }

   @IgrpQueryHandler
    public ResponseEntity<IGRPUserDTO> handle(GetCurrentUserQuery query) {
        // Recupera o ID do usuário autenticado
        Long userId = SecurityUtils.getCurrentUserId(); // adapte conforme sua segurança

        Optional<IGRPUser> optionalUser = igrpUserRepository.findById(query.getId());
        if (optionalUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        IGRPUserDTO dto = userMapper.toDto(optionalUser.get());
        return ResponseEntity.ok(dto);
    }

}