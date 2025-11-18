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
                )) AND m.status = 'ACTIVE' AND (m.type = 'MENU_PAGE' OR m.type = 'EXTERNAL_PAGE')
            """)
    List<MenuEntryEntity> findAvailableMenusForDepartment(@Param("code") String code);

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
    @Query("""
        SELECT m
        FROM MenuEntryEntity m
        JOIN m.applicationId a
        JOIN a.departments d
        JOIN d.roles r
        JOIN r.users u
        WHERE u = :user AND m.status <> 'DELETED' AND a = :application
    """)
    List<MenuEntryEntity> findByApplicationIdAndUserIdAndStatusNotDeleted(IGRPUserEntity user, ApplicationEntity application);

}