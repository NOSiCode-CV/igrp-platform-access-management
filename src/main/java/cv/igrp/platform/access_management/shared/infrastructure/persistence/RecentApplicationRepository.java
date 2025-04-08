package cv.igrp.platform.access_management.shared.infrastructure.persistence;

import cv.igrp.platform.access_management.shared.domain.models.RecentApplication;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;

import org.springframework.data.repository.history.RevisionRepository;

@Repository
public interface RecentApplicationRepository extends
    JpaRepository<RecentApplication, Integer>,
    JpaSpecificationExecutor<RecentApplication>,
    RevisionRepository<RecentApplication, Integer, Integer>
{

}