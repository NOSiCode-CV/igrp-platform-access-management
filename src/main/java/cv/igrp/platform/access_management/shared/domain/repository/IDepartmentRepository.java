package cv.igrp.platform.access_management.shared.domain.repository;

import cv.igrp.platform.access_management.shared.domain.models.Department;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public interface IDepartmentRepository {

  /**
  * Save or update a Department.
  *
  * @param department is the object to be saved
  * @return the Department object that was saved
  */
  Department save(Department department);

  /**
  * Fetch a Department by its ID.
  *
  * @param id the Department's ID
  * @return an Optional Department, if found
  */
  Optional<Department> findById(Integer id);

  /**
  * Fetch all the Department objects.
  *
  * @return all Department objects
  */
  List<Department> findAll();

  /**
  * Deletes a Department by its ID
  *
  * @param id the Department's ID
  */
  void deleteById(Integer id);

}
