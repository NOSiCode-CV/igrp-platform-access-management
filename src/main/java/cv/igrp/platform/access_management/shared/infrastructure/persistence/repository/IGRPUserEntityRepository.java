package cv.igrp.platform.access_management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;

import org.springframework.data.repository.history.RevisionRepository;

@Repository
public interface IGRPUserEntityRepository extends
    JpaRepository<IGRPUserEntity, Integer>,
    JpaSpecificationExecutor<IGRPUserEntity>,
    RevisionRepository<IGRPUserEntity, Integer, Integer>
{

    @Query("""
        select u from IGRPUserEntity u where u.externalId = :externalId and u.status != 'DELETED'
    """)
    Optional<IGRPUserEntity> findByExternalId(String externalId);


    @Query("""
        select case when count(u) > 0 then true else false end from IGRPUserEntity u where u.username = :username and u.status != 'DELETED'
    """)
    boolean existsByUsername(String username);

    @Query("""
        select case when count(u) > 0 then true else false end from IGRPUserEntity u where u.email = :email and u.status != 'DELETED'
    """)
    boolean existsByEmail(String email);

}