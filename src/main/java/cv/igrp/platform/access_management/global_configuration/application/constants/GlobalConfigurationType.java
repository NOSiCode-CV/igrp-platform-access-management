package cv.igrp.platform.access_management.global_configuration.application.constants;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.Getter;

@Getter
public enum GlobalConfigurationType {

  CLUSTER(
    "CLUSTER", 
    "Cluster"
  ),
    ORGANIZATION(
    "ORGANIZATION", 
    "Organization"
  )
  ;

  private final String code;
  private final String description;

  GlobalConfigurationType(String code, String description) {
    this.code = code;
    this.description = description;
  }


  /**
  * Pre-built maps for fast lookup.
  */
  private static final Map<String, GlobalConfigurationType> CODE_MAP = Arrays.stream(values()).collect(Collectors.toMap(GlobalConfigurationType::getCode, Function.identity()));

    /**
    * Returns the enum constant matching the code.
    */
  public static Optional<GlobalConfigurationType> fromCode(String code) {
    return Optional.ofNullable(CODE_MAP.get(code));
  }

    /**
    * Returns a map of code to description.
    */
  public static Map<String, String> codeDescriptionMap() {
    return Arrays.stream(values()).collect(Collectors.toMap(GlobalConfigurationType::getCode, GlobalConfigurationType::getDescription));
  }

}
