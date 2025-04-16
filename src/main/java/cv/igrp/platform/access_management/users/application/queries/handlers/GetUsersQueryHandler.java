package cv.igrp.platform.access_management.users.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.UserRepository;
import cv.igrp.platform.access_management.shared.domain.models.User;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.users.application.queries.queries.GetUsersQuery;
import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GetUsersQueryHandler implements QueryHandler<GetUsersQuery, ResponseEntity<List<IGRPUserDTO>>> {

    private final UserRepository userRepository;
    private final IGRPUserMapper userMapper;

    public GetUsersQueryHandler(UserRepository userRepository, IGRPUserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @IgrpQueryHandler
    public ResponseEntity<List<IGRPUserDTO>> handle(GetUsersQuery query) {
        /*List<User> users = userRepository.findUsers(query.getApplicationId(), query.getDepartmentId(),
                query.getName(), query.getUsername(), query.getEmail());
        List<IGRPUserDTO> result = users.stream().map(userMapper::toDto).collect(Collectors.toList());*/
        return ResponseEntity.ok(new ArrayList<>());
    }
}