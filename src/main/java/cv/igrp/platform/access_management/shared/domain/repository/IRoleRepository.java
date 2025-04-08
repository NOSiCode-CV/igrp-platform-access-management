package cv.igrp.platform.access_management.shared.domain.repository;

import cv.igrp.platform.access_management.shared.domain.models.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public interface IRoleRepository {

  /**
  * Save or update a Role.
  *
  * @param role is the object to be saved
  * @return the Role object that was saved
  */
  Role save(Role role);

  /**
  * Fetch a Role by its ID.
  *
  * @param id the Role's ID
  * @return an Optional Role, if found
  */
  Optional<Role> findById(String id);

  /**
  * Fetch all the Role objects.
  *
  * @return all Role objects
  */
  List<Role> findAll();

  /**
  * Deletes a Role by its ID
  *
  * @param id the Role's ID
  */
  void deleteById(String id);

}
