package cv.igrp.platform.access_management.app.application.commands.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddApplicationCustomFieldsCommand implements Command {

  
  private Map<String, ?> addApplicationCustomFieldsRequest;
  @NotNull(message = "The field <id> is required.")
  private Integer id;

}