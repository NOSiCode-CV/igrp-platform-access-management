package cv.igrp.platform.access_management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.FavoriteApplicationEntity;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.http.HttpStatus;

import java.util.Optional;


@Repository
public interface FavoriteApplicationEntityRepository extends
        JpaRepository<FavoriteApplicationEntity, Integer>,
        JpaSpecificationExecutor<FavoriteApplicationEntity> {

    default FavoriteApplicationEntity findByIdOrThrow(Integer id) {
        return this.findById(id)
                .orElseThrow(() -> IgrpResponseStatusException.of(HttpStatus.NOT_FOUND, "FavoriteApplicationEntity not found for id: " + id));
    }

    @Query("""
       SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END
       FROM FavoriteApplicationEntity f
       JOIN f.applications a
       WHERE f.userId = :user
         AND a = :application
       """)
    boolean existsByUserAndApplication(@Param("user") String user,
                                       @Param("application") ApplicationEntity application);

    Optional<FavoriteApplicationEntity> findByUserId(String user);

}