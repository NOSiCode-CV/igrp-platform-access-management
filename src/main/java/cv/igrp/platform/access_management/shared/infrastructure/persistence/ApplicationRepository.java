package cv.igrp.platform.access_management.shared.infrastructure.persistence;

import cv.igrp.platform.access_management.shared.domain.models.Application;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;

import org.springframework.data.repository.history.RevisionRepository;

@Repository
public interface ApplicationRepository extends
    JpaRepository<Application, Integer>,
    JpaSpecificationExecutor<Application>,
    RevisionRepository<Application, Integer, Integer>
{

}