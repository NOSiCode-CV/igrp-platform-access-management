package cv.igrp.platform.access_management.resource.application.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddResourceCustomFieldsCommand implements Command {

  
  private Map<String, ?> addResourceCustomFieldsRequest;
  @NotBlank(message = "The field <name> is required")
  private String name;

}