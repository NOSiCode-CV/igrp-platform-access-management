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
    private final PermissionCheckService permissionCheckService;

    public DatabaseAuthorizeApiAdapter(PermissionCheckService permissionCheckService) {
        this.permissionCheckService = permissionCheckService;
    }

    @Override
    public CompletableFuture<PermissionCheckResponse> check(PermissionCheckRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.nanoTime();
            PermissionCacheEntryDTO dto = permissionCheckService.getOrLoadPermission(request);


            PermissionCheckResponse response = new PermissionCheckResponse();

            response.setAllowed(dto.allowed());
            response.setViaRoles(dto.viaRoles());
            response.setReason(dto.reason());
            // Every check reads the database; the per-check cache is gone (#18).
            response.setCacheHit(false);
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
