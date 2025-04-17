package cv.igrp.platform.access_management.users.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.users.application.commands.commands.GetIGRPUserByIdCommand;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.IGRPUserRepository;
import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import jakarta.persistence.EntityNotFoundException;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
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
        IGRPUser user = igrpUserRepository.findById(command.getId())
            .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + command.getId()));
        users = List.of(user); // Cria uma lista com um único usuário
    } else {
        users = igrpUserRepository.findAll(); // OK, já retorna uma lista
    }

    List<IGRPUserDTO> userDTOs = users.stream()
            .map(igrpUserMapper::toDto)
            .collect(Collectors.toList());

    return ResponseEntity.ok(userDTOs);
}

}