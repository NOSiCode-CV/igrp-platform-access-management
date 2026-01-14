package cv.igrp.platform.access_management.files.application.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.jdbc.BinaryJdbcType;
import org.springframework.web.multipart.MultipartFile;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadPrivateFileCommand implements Command {

  @NotNull(message = "The field <file> is required")
  private MultipartFile file;
  @NotBlank(message = "The field <folder> is required")
  private String folder;

}