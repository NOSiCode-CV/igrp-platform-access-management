package cv.igrp.platform.access_management.shared.domain.repository;

import cv.igrp.platform.access_management.shared.domain.models.MenuEntry;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public interface IMenuEntryRepository {

  /**
  * Save or update a MenuEntry.
  *
  * @param menuentry is the object to be saved
  * @return the MenuEntry object that was saved
  */
  MenuEntry save(MenuEntry menuentry);

  /**
  * Fetch a MenuEntry by its ID.
  *
  * @param id the MenuEntry's ID
  * @return an Optional MenuEntry, if found
  */
  Optional<MenuEntry> findById(Integer id);

  /**
  * Fetch all the MenuEntry objects.
  *
  * @return all MenuEntry objects
  */
  List<MenuEntry> findAll();

  /**
  * Deletes a MenuEntry by its ID
  *
  * @param id the MenuEntry's ID
  */
  void deleteById(Integer id);

}
