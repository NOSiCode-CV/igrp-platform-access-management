package cv.igrp.platform.access_management.resource.application.commands.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.resource.application.dto.ResourceItemDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RemoveItemsCommand implements Command {

  
  private ResourceItemDTO resourceitemdto;
  @NotNull(message = "The field <id> is required.")
  private Integer id;

}