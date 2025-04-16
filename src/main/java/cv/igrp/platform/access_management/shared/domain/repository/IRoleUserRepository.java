package cv.igrp.platform.access_management.shared.domain.repository;

import cv.igrp.platform.access_management.shared.domain.models.RoleUser;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public interface IRoleUserRepository {

  /**
  * Save or update a RoleUser.
  *
  * @param roleuser is the object to be saved
  * @return the RoleUser object that was saved
  */
  RoleUser save(RoleUser roleuser);

  /**
  * Fetch a RoleUser by its ID.
  *
  * @param id the RoleUser's ID
  * @return an Optional RoleUser, if found
  */
  Optional<RoleUser> findById(Integer id);

  /**
  * Fetch all the RoleUser objects.
  *
  * @return all RoleUser objects
  */
  List<RoleUser> findAll();

  /**
  * Deletes a RoleUser by its ID
  *
  * @param id the RoleUser's ID
  */
  void deleteById(Integer id);

}
