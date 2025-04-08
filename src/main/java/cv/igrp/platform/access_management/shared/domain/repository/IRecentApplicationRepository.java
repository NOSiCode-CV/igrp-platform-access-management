package cv.igrp.platform.access_management.shared.domain.repository;

import cv.igrp.platform.access_management.shared.domain.models.RecentApplication;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public interface IRecentApplicationRepository {

  /**
  * Save or update a RecentApplication.
  *
  * @param recentapplication is the object to be saved
  * @return the RecentApplication object that was saved
  */
  RecentApplication save(RecentApplication recentapplication);

  /**
  * Fetch a RecentApplication by its ID.
  *
  * @param id the RecentApplication's ID
  * @return an Optional RecentApplication, if found
  */
  Optional<RecentApplication> findById(Integer id);

  /**
  * Fetch all the RecentApplication objects.
  *
  * @return all RecentApplication objects
  */
  List<RecentApplication> findAll();

  /**
  * Deletes a RecentApplication by its ID
  *
  * @param id the RecentApplication's ID
  */
  void deleteById(Integer id);

}
