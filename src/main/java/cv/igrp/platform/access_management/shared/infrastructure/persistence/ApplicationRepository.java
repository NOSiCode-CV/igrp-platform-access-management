package cv.igrp.platform.access_management.shared.infrastructure.persistence;

import cv.igrp.platform.access_management.shared.domain.models.Application;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.history.RevisionRepository;

@Repository
public interface ApplicationRepository extends
    JpaRepository<Application, Integer>,
    JpaSpecificationExecutor<Application>,
    RevisionRepository<Application, Integer, Integer>
{
    List<Application> findDistinctByDepartments_Roles_Users_UsernameOrDepartments_Roles_Users_Email(String username, String email);


    @Query("""
        SELECT a FROM Application a
        WHERE a.id NOT IN (
            SELECT DISTINCT app.id FROM Application app
            JOIN app.departments d
            JOIN d.roles r
            JOIN r.users u
            WHERE u.username = :uid OR u.email = :uid
        )
    """)
    List<Application> findDeniedApplications(@Param("uid") String uid);

}