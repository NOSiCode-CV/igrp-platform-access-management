package cv.igrp.platform.access_management.authorization.domain.service;


import cv.igrp.framework.auth.core.authorization.model.PermissionCheckRequest;
import cv.igrp.framework.auth.core.authorization.model.PermissionCheckResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PermissionCacheService {

    private static final String CACHE_NAME = "permissionCache";

    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionCacheService.class);

    @Cacheable(value = CACHE_NAME,
            key = "T(java.util.Objects).hash(#request.subject, #request.resource, #request.action)",
            unless = "#result == null || #result.isEmpty()")
    public Optional<PermissionCheckResponse> getPermission(PermissionCheckRequest request) {
        LOGGER.info("Cache MISS - Buscando do banco de dados: {}", request);
        return Optional.empty();
    }

    @CachePut(value = CACHE_NAME,
            key = "T(java.util.Objects).hash(#request.subject, #request.resource, #request.action)")
    public Optional<PermissionCheckResponse> cachePermission(PermissionCheckRequest request,
                                                             PermissionCheckResponse response) {
        LOGGER.info("Armazenando no cache: {}", request);
        return Optional.of(response);
    }
}
