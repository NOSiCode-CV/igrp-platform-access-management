package cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.ServiceAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServiceAccountJpaRepository extends JpaRepository<ServiceAccountEntity, UUID> {

    boolean existsByOauthClient_Id(UUID oauthClientId);

    Optional<ServiceAccountEntity> findByOauthClient_ClientId(String clientId);

    @Query("""
            select distinct sa from ServiceAccountEntity sa
            left join fetch sa.roleAssignments assignment
            left join fetch assignment.role role
            left join fetch role.permissions permission
            left join fetch sa.oauthClient client
            left join fetch sa.application app
            where sa.id = :id
            """)
    Optional<ServiceAccountEntity> findByIdWithRolesAndPermissions(@Param("id") UUID id);

    @Query("""
            select distinct sa from ServiceAccountEntity sa
            left join fetch sa.roleAssignments assignment
            left join fetch assignment.role role
            left join fetch role.permissions permission
            left join fetch sa.oauthClient client
            left join fetch sa.application app
            where client.clientId = :clientId
            """)
    Optional<ServiceAccountEntity> findByOauthClientClientIdWithRolesAndPermissions(@Param("clientId") String clientId);
}
