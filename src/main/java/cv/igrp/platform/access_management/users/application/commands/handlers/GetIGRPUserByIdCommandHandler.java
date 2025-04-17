package cv.igrp.platform.access_management.users.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.users.application.commands.commands.GetIGRPUserByIdCommand;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.IGRPUserRepository;
import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GetIGRPUserByIdCommandHandler implements CommandHandler<GetIGRPUserByIdCommand, ResponseEntity<List<IGRPUserDTO>>> {

    private final IGRPUserRepository igrpUserRepository;
    private final IGRPUserMapper igrpUserMapper;

    public GetIGRPUserByIdCommandHandler(IGRPUserRepository igrpUserRepository, IGRPUserMapper igrpUserMapper) {
        this.igrpUserRepository = igrpUserRepository;
        this.igrpUserMapper = igrpUserMapper;
    }

    @IgrpQueryHandler
    public ResponseEntity<List<IGRPUserDTO>> handle(GetIGRPUserByIdCommand command) {
        List<IGRPUser> users;

        if (command.getId() != null) {
            users = igrpUserRepository.findAllById(List.of(command.getId().toString()));
        } else {
            // Caso o ID não seja passado, faça uma busca com outros filtros, se necessário.
            users = igrpUserRepository.findAll();
        }

        // Mapeia os usuários para DTOs e retorna a resposta
        List<IGRPUserDTO> userDTOs = new ArrayList<>(); /*users.stream()
                //.map(igrpUserMapper::toDto)
                .collect(Collectors.toList());*/

        return ResponseEntity.ok(userDTOs);
    }
}