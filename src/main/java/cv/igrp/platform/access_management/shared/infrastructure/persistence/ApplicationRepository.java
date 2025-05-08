package cv.igrp.platform.access_management.shared.infrastructure.persistence;

import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.domain.models.Department;
import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.history.RevisionRepository;

/**
 * Repository interface for managing {@link Application} entities.
 *
 * <p>
 * Extends:
 * <ul>
 *     <li>{@link JpaRepository} for basic CRUD operations</li>
 *     <li>{@link JpaSpecificationExecutor} for dynamic query execution using specifications</li>
 *     <li>{@link RevisionRepository} to support versioning and auditing of application changes</li>
 * </ul>
 *
 * <p>
 * Includes custom query methods to retrieve applications based on user associations and access restrictions.
 * </p>
 *
 * @see Application
 * @see Department
 * @see Role
 * @see IGRPUser
 * @see JpaRepository
 * @see JpaSpecificationExecutor
 * @see RevisionRepository
 */
@Repository
public interface ApplicationRepository extends
    JpaRepository<Application, Integer>,
    JpaSpecificationExecutor<Application>,
    RevisionRepository<Application, Integer, Integer>
{
    List<Application> findDistinctByDepartments_Roles_Users_UsernameOrDepartments_Roles_Users_Email(String username, String email);


    @Query("""
        SELECT a FROM Application a
        WHERE NOT EXISTS (
            SELECT null FROM Application app
            JOIN app.departments d
            JOIN d.roles r
            JOIN r.users u
            WHERE app.id=a.id and u.username = :uid OR u.email = :uid
        )
    """)
    List<Application> findDeniedApplications(@Param("uid") String uid);

}