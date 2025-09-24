package cv.igrp.platform.access_management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationEntityRepository extends
        JpaRepository<ApplicationEntity, Integer>,
        JpaSpecificationExecutor<ApplicationEntity>,
        RevisionRepository<ApplicationEntity, Integer, Integer> {

    @Query("""
            SELECT DISTINCT a
            FROM ApplicationEntity a
            JOIN a.departments d
            JOIN d.roles r
            JOIN r.users u
            WHERE a.status <> :status AND (u.username = :username OR u.email = :email)
            """)
    List<ApplicationEntity> findApplicationsByUserOrEmailAndStatusNot(
            @Param("username") String username,
            @Param("email") String email,
            @Param("status") Status status
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

    // Case 1: Application is directly owned by the department
    // Case 2: Application is shared with the department
    // Case 3: Application is inherited from the parent department
    // Exclude applications already attributed to this department
    @Query("""
    SELECT DISTINCT a
    FROM ApplicationEntity a
    JOIN a.departments dParent
    WHERE (
       
        dParent.code = :code
        OR
        
        EXISTS (
            SELECT 1
            FROM DepartmentEntity d
            JOIN d.sharedApplications sa
            WHERE d.code = :code AND sa.id = a.id
        )
        OR
        
        EXISTS (
            SELECT 1
            FROM DepartmentEntity child
            JOIN child.parentId p
            JOIN p.applications pa
            WHERE child.code = :code AND pa.id = a.id
        )
    )
    AND NOT EXISTS (
        
        SELECT 1
        FROM DepartmentEntity d2
        JOIN d2.applications da
        WHERE d2.code = :code AND da.id = a.id
    )
""")
    List<ApplicationEntity> findAvailableApplicationsForDepartment(@Param("code") String code);

    Optional<ApplicationEntity> findByCodeAndStatusNot(String code, Status status);

    List<ApplicationEntity> findByIdInAndStatusNot(Collection<Integer> ids, Status status);

}