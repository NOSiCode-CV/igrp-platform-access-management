package cv.igrp.platform.access_management.shared.domain.repository;

import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public interface IIGRPUserRepository {

  /**
  * Save or update a IGRPUser.
  *
  * @param igrpuser is the object to be saved
  * @return the IGRPUser object that was saved
  */
  IGRPUser save(IGRPUser igrpuser);

  /**
  * Fetch a IGRPUser by its ID.
  *
  * @param id the IGRPUser's ID
  * @return an Optional IGRPUser, if found
  */
  Optional<IGRPUser> findById(String id);

  /**
  * Fetch all the IGRPUser objects.
  *
  * @return all IGRPUser objects
  */
  List<IGRPUser> findAll();

  /**
  * Deletes a IGRPUser by its ID
  *
  * @param id the IGRPUser's ID
  */
  void deleteById(String id);

}
