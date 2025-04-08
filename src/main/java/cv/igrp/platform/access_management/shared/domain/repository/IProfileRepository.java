package cv.igrp.platform.access_management.shared.domain.repository;

import cv.igrp.platform.access_management.shared.domain.models.Profile;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public interface IProfileRepository {

  /**
  * Save or update a Profile.
  *
  * @param profile is the object to be saved
  * @return the Profile object that was saved
  */
  Profile save(Profile profile);

  /**
  * Fetch a Profile by its ID.
  *
  * @param id the Profile's ID
  * @return an Optional Profile, if found
  */
  Optional<Profile> findById(String id);

  /**
  * Fetch all the Profile objects.
  *
  * @return all Profile objects
  */
  List<Profile> findAll();

  /**
  * Deletes a Profile by its ID
  *
  * @param id the Profile's ID
  */
  void deleteById(String id);

}
