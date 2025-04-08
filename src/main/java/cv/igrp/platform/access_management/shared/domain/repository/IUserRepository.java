package cv.igrp.platform.access_management.shared.domain.repository;

import cv.igrp.platform.access_management.shared.domain.models.User;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public interface IUserRepository {

  /**
  * Save or update a User.
  *
  * @param user is the object to be saved
  * @return the User object that was saved
  */
  User save(User user);

  /**
  * Fetch a User by its ID.
  *
  * @param id the User's ID
  * @return an Optional User, if found
  */
  Optional<User> findById(String id);

  /**
  * Fetch all the User objects.
  *
  * @return all User objects
  */
  List<User> findAll();

  /**
  * Deletes a User by its ID
  *
  * @param id the User's ID
  */
  void deleteById(String id);

}
