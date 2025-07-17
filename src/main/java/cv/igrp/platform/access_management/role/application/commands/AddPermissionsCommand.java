package cv.igrp.platform.access_management.role.application.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddPermissionsCommand implements Command {

  
  private List<Integer> addPermissionsRequest;
  @NotNull(message = "The field <id> is required.")
  private Integer id;

}