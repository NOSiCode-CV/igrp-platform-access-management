package cv.igrp.platform.access_management.shared.infrastructure.persistence;

import cv.igrp.platform.access_management.shared.domain.models.Role;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;



@Repository
public interface RoleRepository extends
    JpaRepository<Role, Integer>,
    JpaSpecificationExecutor<Role>
    
{

    Optional<Role> findById(Integer id);
}