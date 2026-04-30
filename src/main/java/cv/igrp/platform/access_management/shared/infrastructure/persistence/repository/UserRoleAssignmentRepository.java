package cv.igrp.platform.access_management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserRoleAssignment;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, UserRoleId> {

    @Query("SELECT ura FROM UserRoleAssignment ura WHERE ura.user.id = :userId AND (ura.expiresAt IS NULL OR ura.expiresAt > CURRENT_TIMESTAMP)")
    List<UserRoleAssignment> findActiveByUserId(@Param("userId") Integer userId);

    @Query("SELECT ura FROM UserRoleAssignment ura WHERE ura.expiresAt IS NOT NULL AND ura.expiresAt <= CURRENT_TIMESTAMP")
    List<UserRoleAssignment> findExpiredRoles();
}
