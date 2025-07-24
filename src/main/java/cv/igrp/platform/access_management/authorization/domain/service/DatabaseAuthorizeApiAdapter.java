package cv.igrp.platform.access_management.authorization.domain.service;

import cv.igrp.framework.auth.core.authorization.model.PermissionCheckRequest;
import cv.igrp.framework.auth.core.authorization.model.PermissionCheckResponse;
import cv.igrp.framework.auth.core.authorization.model.RelationshipTuple;
import cv.igrp.framework.auth.core.authorization.service.AuthorizationCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


public class DatabaseAuthorizeApiAdapter implements AuthorizationCore {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseAuthorizeApiAdapter.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseAuthorizeApiAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;

    }

    @Override
    public CompletableFuture<PermissionCheckResponse> check(PermissionCheckRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.nanoTime();

            //LOGGER.info("Checking authorization for user '{}' with resource '{}'", username, request.getResource());

            String subject = request.getSubject();
            String resource = request.getResource();
            String action = request.getAction();

            boolean allowed = checkPermission(subject, resource, action);

            PermissionCheckResponse response = new PermissionCheckResponse();

            response.setAllowed(allowed);
            response.setViaRoles(Collections.emptyList());
            response.setCacheHit(false);
            response.setReason(allowed ? "Permission granted" : "Permission denied");
            response.setResolutionTimeMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));

            LOGGER.debug("Redis authorization result for subject '{}': {}", request.getSubject(), response);
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


    private Boolean checkPermission(String username, String resourceItem, String permissionName) {
        String sql = """
               select 1 from t_user u
                left join t_role_users ru on u.id=ru.users_id
                left join t_role r on ru.roles_id=r.id
                left join t_role_permission rp on rp.role_id = r.id
                left join t_permission p on p.id = rp.permission
                left join t_resource_item ri on ri.permission_id = p.id
                where u.username = ?
                      and ri.name = ?
                      and p.name = ?
                limit 1;
               """;

        List<Integer> results = jdbcTemplate.query(sql, (_, _) -> 1, username, resourceItem, permissionName);

        return !results.isEmpty();
    }
}
