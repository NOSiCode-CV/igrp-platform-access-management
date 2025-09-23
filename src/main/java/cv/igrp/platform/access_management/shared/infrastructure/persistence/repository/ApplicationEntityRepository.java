package cv.igrp.platform.access_management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.shared.application.constants.AppType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.history.RevisionRepository;

@Repository
public interface ApplicationEntityRepository extends
    JpaRepository<ApplicationEntity, Integer>,
    JpaSpecificationExecutor<ApplicationEntity>,
    RevisionRepository<ApplicationEntity, Integer, Integer>
{

    List<ApplicationEntity> findDistinctByDepartmentId_Roles_Users_UsernameOrDepartmentId_Roles_Users_EmailAndStatusNot(
            String username,
            String email,
            Status status
    );

    @Query("""
                SELECT a FROM ApplicationEntity a
                     WHERE NOT EXISTS (
                         SELECT 1
                         FROM ApplicationEntity app
                         JOIN app.departments d
                         JOIN d.roles r
                         JOIN r.users u
                         WHERE app.id = a.id AND (u.username = :uid OR u.email = :uid)
                     )
            """)
    List<ApplicationEntity> findDeniedApplications(@Param("uid") String uid);

    Optional<ApplicationEntity> findFirstByType(AppType type);

    boolean existsByType(AppType type);

    Optional<ApplicationEntity> findByCodeAndStatusNot(String code, Status status);

    List<ApplicationEntity> findByIdInAndStatusNot(Collection<Integer> ids, Status status);

}