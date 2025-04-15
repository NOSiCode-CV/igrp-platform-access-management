package cv.igrp.platform.access_management.shared.domain.repository;

import cv.igrp.platform.access_management.shared.domain.models.ResourceItem;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public interface IResourceItemRepository {

  /**
  * Save or update a ResourceItem.
  *
  * @param resourceitem is the object to be saved
  * @return the ResourceItem object that was saved
  */
  ResourceItem save(ResourceItem resourceitem);

  /**
  * Fetch a ResourceItem by its ID.
  *
  * @param id the ResourceItem's ID
  * @return an Optional ResourceItem, if found
  */
  Optional<ResourceItem> findById(Integer id);

  /**
  * Fetch all the ResourceItem objects.
  *
  * @return all ResourceItem objects
  */
  List<ResourceItem> findAll();

  /**
  * Deletes a ResourceItem by its ID
  *
  * @param id the ResourceItem's ID
  */
  void deleteById(Integer id);

}
