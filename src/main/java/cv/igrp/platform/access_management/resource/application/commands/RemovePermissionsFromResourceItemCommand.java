package cv.igrp.platform.access_management.resource.application.commands;

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
public class RemovePermissionsFromResourceItemCommand implements Command {

  
  private List<String> removePermissionsFromResourceItemRequest;
  @NotBlank(message = "The field <name> is required")
  private String name;

}