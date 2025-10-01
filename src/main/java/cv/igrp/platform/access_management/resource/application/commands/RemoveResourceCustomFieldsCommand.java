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
public class RemoveResourceCustomFieldsCommand implements Command {

  
  private List<String> removeResourceCustomFieldsRequest;
  @NotBlank(message = "The field <name> is required")
  private String name;

}