package cv.igrp.platform.access_management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResourceEntityRepository extends
    JpaRepository<ResourceEntity, Integer>,
    JpaSpecificationExecutor<ResourceEntity>,
    RevisionRepository<ResourceEntity, Integer, Integer>
{

    Optional<ResourceEntity> findByNameAndStatusNot(String name, Status status);

    default ResourceEntity findByNameNotDeleted(String name) {
        return findByNameAndStatusNot(name, Status.DELETED)
                .orElseThrow(() -> IgrpResponseStatusException.badRequest("Resource not found with name: " + name));
    }

    @Query("""
                SELECT DISTINCT r
                 FROM ResourceEntity r
                 WHERE (( 1=1 AND
                             NOT EXISTS (
                                 SELECT 1
                                 FROM DepartmentEntity d
                                 WHERE d.code = :code AND d.parentId IS NOT NULL
                             )
                             AND r.id NOT IN (
                                 SELECT dr.id
                                 FROM DepartmentEntity d2
                                 JOIN d2.resources dr
                                 WHERE d2.code = :code
                             )
                         )
                         OR
                             EXISTS (
                                 SELECT 1
                                 FROM DepartmentEntity child
                                 JOIN child.parentId p
                                 JOIN p.resources pr
                                 WHERE child.code = :code
                                   AND pr.id = r.id
                                   AND r.id NOT IN (
                                       SELECT dr2.id
                                       FROM DepartmentEntity d3
                                       JOIN d3.resources dr2
                                       WHERE d3.code = :code
                                   )
                             )
                         )
                     AND r.status = 'ACTIVE'
            """)
    List<ResourceEntity> findAvailableResourcesForDepartment(@Param("code") String code, @Param("system_resource") String systemResource);

    @Query(
    """
                SELECT r
                FROM ResourceEntity r
                JOIN r.departments d
                WHERE d = :department AND r.status != :status
    """
    )
    List<ResourceEntity> findByDepartmentAndStatusNot(DepartmentEntity department, Status status);
}