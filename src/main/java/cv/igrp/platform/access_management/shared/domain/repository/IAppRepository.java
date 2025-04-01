package cv.igrp.platform.access_management.shared.domain.repository;

import cv.igrp.platform.access_management.shared.domain.models.App;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public interface IAppRepository {

  /**
  * Save or update a App.
  *
  * @param app is the object to be saved
  * @return the App object that was saved
  */
  App save(App app);

  /**
  * Fetch a App by its ID.
  *
  * @param id the App's ID
  * @return an Optional App, if found
  */
  Optional<App> findById(Integer id);

  /**
  * Fetch all the App objects.
  *
  * @return all App objects
  */
  List<App> findAll();

  /**
  * Deletes a App by its ID
  *
  * @param id the App's ID
  */
  void deleteById(Integer id);

}
