package cv.igrp.platform.access_management.shared.domain.repository;

import cv.igrp.platform.access_management.shared.domain.models.Permission;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public interface IPermissionRepository {

  /**
  * Save or update a Permission.
  *
  * @param permission is the object to be saved
  * @return the Permission object that was saved
  */
  Permission save(Permission permission);

  /**
  * Fetch a Permission by its ID.
  *
  * @param id the Permission's ID
  * @return an Optional Permission, if found
  */
  Optional<Permission> findById(Integer id);

  /**
  * Fetch all the Permission objects.
  *
  * @return all Permission objects
  */
  List<Permission> findAll();

  /**
  * Deletes a Permission by its ID
  *
  * @param id the Permission's ID
  */
  void deleteById(Integer id);

}
