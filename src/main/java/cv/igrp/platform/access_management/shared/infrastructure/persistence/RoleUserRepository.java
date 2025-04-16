package cv.igrp.platform.access_management.shared.infrastructure.persistence;

import cv.igrp.platform.access_management.shared.domain.models.RoleUser;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;

import org.springframework.data.repository.history.RevisionRepository;

@Repository
public interface RoleUserRepository extends
    JpaRepository<RoleUser, Integer>,
    JpaSpecificationExecutor<RoleUser>,
    RevisionRepository<RoleUser, Integer, Integer>
{

}