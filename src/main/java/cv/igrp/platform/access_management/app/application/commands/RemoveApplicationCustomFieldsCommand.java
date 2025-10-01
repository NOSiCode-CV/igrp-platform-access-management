package cv.igrp.platform.access_management.app.application.commands;

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
public class RemoveApplicationCustomFieldsCommand implements Command {

  
  private List<String> removeApplicationCustomFieldsRequest;
  @NotBlank(message = "The field <code> is required")
  private String code;

}