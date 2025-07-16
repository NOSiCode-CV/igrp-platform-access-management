package cv.igrp.platform.access_management.resource.application.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.resource.application.dto.ResourceItemDTO;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddItemsCommand implements Command {

  
  private List<ResourceItemDTO> resourceitemdto;
  @NotNull(message = "The field <id> is required.")
  private Integer id;

}