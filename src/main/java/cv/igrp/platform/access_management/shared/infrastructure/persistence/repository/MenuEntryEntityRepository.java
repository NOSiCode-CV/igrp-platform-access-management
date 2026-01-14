package cv.igrp.platform.access_management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.shared.application.constants.MenuEntryType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.history.RevisionRepository;

@Repository
public interface MenuEntryEntityRepository extends
        JpaRepository<MenuEntryEntity, Integer>,
        JpaSpecificationExecutor<MenuEntryEntity>,
        RevisionRepository<MenuEntryEntity, Integer, Integer> {

    List<MenuEntryEntity> findByApplicationIdAndStatusIn(ApplicationEntity appId, List<Status> status);

    List<MenuEntryEntity> findByApplicationIdAndTypeInAndStatusIn(ApplicationEntity appId, List<MenuEntryType> types, List<Status> status);

    Optional<MenuEntryEntity> findByApplicationIdAndCodeAndStatusNot(ApplicationEntity appId, String code, Status status);

    @Query("""
                SELECT DISTINCT m
                FROM MenuEntryEntity m
                JOIN m.applicationId a
                JOIN a.departments dParent
                WHERE ((
                    dParent.code = :code
                    OR EXISTS (
                        SELECT 1
                        FROM DepartmentEntity child
                        JOIN child.parentId p
                        JOIN child.menuentries cm
                        WHERE p.code = :code AND cm.id = m.id
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM DepartmentEntity child
                        JOIN child.parentId p
                        JOIN p.menuentries pm
                        WHERE child.code = :code AND pm.id = m.id
                    )
                )
                AND NOT EXISTS (
                    SELECT 1
                    FROM DepartmentEntity d2
                    JOIN d2.menuentries dm
                    WHERE d2.code = :code AND dm.id = m.id
                )) AND m.status = 'ACTIVE' AND (m.type = 'MENU_PAGE' OR m.type = 'EXTERNAL_PAGE' OR m.type = 'SYSTEM_PAGE') AND a = :application
            """)
    List<MenuEntryEntity> findAvailableMenusForDepartment(@Param("code") String code, @Param("application") ApplicationEntity application);

    @Query("""
        SELECT m
        FROM MenuEntryEntity m
        JOIN m.departments d
        WHERE d = :department AND m.status <> :status
    """)
    List<MenuEntryEntity> findByDepartmentAndStatusNot(DepartmentEntity department, Status status);

    /**
     * Finds all menu entries that are not deleted for a given user.
     *
     * @param user the user entity
     * @return a list of menu entry entities
     */
    @Query(value = """
    WITH RECURSIVE menu_tree AS (
        SELECT m.*
        FROM t_menu_entry m
        JOIN t_menu_entry_roles rm ON rm.menu_entry_entity_id = m.id
        JOIN t_role r ON r.id = rm.roles_id
        JOIN t_role_users ur ON ur.roles_id = r.id
        JOIN t_user u ON u.id = ur.users_id
        WHERE u.id = :userId
          AND m.application_id = :applicationId
          AND m.status <> 'DELETED'

        UNION
    
        SELECT parent.*
        FROM t_menu_entry parent
        JOIN menu_tree child ON child.parent_id = parent.id
        WHERE parent.status <> 'DELETED'
    )
    SELECT DISTINCT *
    FROM menu_tree
    ORDER BY position
    """, nativeQuery = true)
    List<MenuEntryEntity> findByApplicationIdAndUserIdAndStatusNotDeleted(@Param("userId") Integer userId,
                                                                          @Param("applicationId") Integer applicationId);

}