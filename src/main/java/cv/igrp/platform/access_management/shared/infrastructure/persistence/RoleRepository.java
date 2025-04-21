package cv.igrp.platform.access_management.shared.infrastructure.persistence;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;



@Repository
public interface RoleRepository extends
    JpaRepository<Role, Integer>,
    JpaSpecificationExecutor<Role>
    
{

    Optional<Role> findById(Integer id);
    List<Role> findByStatusIn(List<Status> statuses);

    List<Role> findByParent(Role parent);
    Optional<Role> findByIdAndStatusNot(Integer id, Status status);

}