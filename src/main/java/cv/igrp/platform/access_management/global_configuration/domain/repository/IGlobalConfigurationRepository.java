package cv.igrp.platform.access_management.global_configuration.domain.repository;

import cv.igrp.platform.access_management.global_configuration.domain.models.GlobalConfiguration;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public interface IGlobalConfigurationRepository {

  /**
  * Save or update a GlobalConfiguration.
  *
  * @param globalconfiguration is the object to be saved
  * @return the GlobalConfiguration object that was saved
  */
  GlobalConfiguration save(GlobalConfiguration globalconfiguration);

  /**
  * Fetch a GlobalConfiguration by its ID.
  *
  * @param id the GlobalConfiguration's ID
  * @return an Optional GlobalConfiguration, if found
  */
  Optional<GlobalConfiguration> findById(Integer id);

  /**
  * Fetch all the GlobalConfiguration objects.
  *
  * @return all GlobalConfiguration objects
  */
  List<GlobalConfiguration> findAll();

  /**
  * Deletes a GlobalConfiguration by its ID
  *
  * @param id the GlobalConfiguration's ID
  */
  void deleteById(Integer id);

}
