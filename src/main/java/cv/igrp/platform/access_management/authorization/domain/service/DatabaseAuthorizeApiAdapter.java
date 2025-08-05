package cv.igrp.platform.access_management.authorization.domain.service;

import cv.igrp.framework.auth.core.authorization.model.PermissionCheckRequest;
import cv.igrp.framework.auth.core.authorization.model.PermissionCheckResponse;
import cv.igrp.framework.auth.core.authorization.model.RelationshipTuple;
import cv.igrp.framework.auth.core.authorization.service.AuthorizationCore;
import cv.igrp.platform.access_management.authorization.application.dto.PermissionCacheEntryDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


public class DatabaseAuthorizeApiAdapter implements AuthorizationCore {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseAuthorizeApiAdapter.class);
    private final PermissionCacheService permissionCacheService;

    public DatabaseAuthorizeApiAdapter(PermissionCacheService cacheService) {
        this.permissionCacheService = cacheService;
    }

    @Override
    public CompletableFuture<PermissionCheckResponse> check(PermissionCheckRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.nanoTime();
            permissionCacheService.setFromCacheAsTrue();
            PermissionCacheEntryDTO dto = permissionCacheService.getOrLoadPermission(request);


            PermissionCheckResponse response = new PermissionCheckResponse();

            response.setAllowed(dto.allowed());
            response.setViaRoles(dto.viaRoles());
            response.setReason(dto.reason());
            response.setCacheHit(permissionCacheService.isFromCache());
            response.setResolutionTimeMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));

            return response;
        });
    }



    @Override
    public CompletableFuture<Void> createRelationships(List<RelationshipTuple> var1) {
        return null;
    }

    @Override
    public CompletableFuture<Void> deleteRelationship(RelationshipTuple var1) {
        return null;
    }

}
