package cv.igrp.platform.access_management.app.application.commands.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RemoveCustomFieldsCommand implements Command {

  
  private List<String> removeCustomFieldsRequest;
  @NotNull(message = "The field <id> is required.")
  private Integer id;

}