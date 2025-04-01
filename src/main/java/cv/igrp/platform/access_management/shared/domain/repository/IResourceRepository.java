package cv.igrp.platform.access_management.shared.domain.repository;

import cv.igrp.platform.access_management.shared.domain.models.Resource;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public interface IResourceRepository {

  /**
  * Save or update a Resource.
  *
  * @param resource is the object to be saved
  * @return the Resource object that was saved
  */
  Resource save(Resource resource);

  /**
  * Fetch a Resource by its ID.
  *
  * @param id the Resource's ID
  * @return an Optional Resource, if found
  */
  Optional<Resource> findById(Integer id);

  /**
  * Fetch all the Resource objects.
  *
  * @return all Resource objects
  */
  List<Resource> findAll();

  /**
  * Deletes a Resource by its ID
  *
  * @param id the Resource's ID
  */
  void deleteById(Integer id);

}
