package cv.igrp.platform.access_management.shared.domain.repository;

import cv.igrp.platform.access_management.shared.domain.models.Application;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public interface IApplicationRepository {

  /**
  * Save or update a Application.
  *
  * @param application is the object to be saved
  * @return the Application object that was saved
  */
  Application save(Application application);

  /**
  * Fetch a Application by its ID.
  *
  * @param id the Application's ID
  * @return an Optional Application, if found
  */
  Optional<Application> findById(Integer id);

  /**
  * Fetch all the Application objects.
  *
  * @return all Application objects
  */
  List<Application> findAll();

  /**
  * Deletes a Application by its ID
  *
  * @param id the Application's ID
  */
  void deleteById(Integer id);

}
