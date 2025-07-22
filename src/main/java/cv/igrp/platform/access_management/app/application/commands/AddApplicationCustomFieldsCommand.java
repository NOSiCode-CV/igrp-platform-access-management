package cv.igrp.platform.access_management.app.application.commands;

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
public class AddApplicationCustomFieldsCommand implements Command {

  
  private Map<String, ?> addApplicationCustomFieldsRequest;
  @NotBlank(message = "The field <code> is required.")
  private String code;

}