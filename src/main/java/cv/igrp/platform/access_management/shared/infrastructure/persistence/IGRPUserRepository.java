package cv.igrp.platform.access_management.shared.infrastructure.persistence;

import cv.igrp.platform.access-management.shared.domain.models.IGRPUser;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;



@Repository
public interface IGRPUserRepository extends
    JpaRepository<IGRPUser, Integer>,
    JpaSpecificationExecutor<IGRPUser>
    
{

}