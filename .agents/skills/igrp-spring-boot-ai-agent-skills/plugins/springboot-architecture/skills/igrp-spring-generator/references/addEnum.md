## Operation: addEnum (EnumConfig)

### Preconditions
- `<basePath>/.igrpstudio/baseApi.json` must exist and define `group`, `packageName`, and `projectStructureStyle`.
- Apply paths and package rules from paths.md.
- `values` must be a non-empty array.

### Schema validation (manifest structure)
The enum manifest must be structurally valid.

Top-level allowed keys (no others):
- `id`, `version`, `type`, `module`, `name`, `attributes`, `values`, `readOnly`

Top-level required keys:
- `name`, `values`

Constraints:
- If `type` is present, it must equal `enum`.
- `name` must follow the naming convention: starts with a letter, then letters/digits/underscore only.
- `module` (if present) must contain only letters/digits/underscore (no spaces).

`attributes` (optional):
- array of objects; each item allowed keys (no others):
  - `type`, `name`, `length`, `unique`, `skipFieldRevision`, `nullable`, `defaultValue`, `generationType`, `sequenceName`, `primaryKey`, `objectType`, `module`, `relation`
- required keys per attribute:
  - `type`, `name`

`values`:
- must be an array
- each item must be an object with only these allowed keys (no others):
  - `name`, `attributes`
- required keys per value:
  - `name`
Rules:
- `attributes` (value-level) is optional; if present, it must be an array of strings.

### 1) Manifest write (exact)
Write the EnumConfig JSON to:
- `.igrpstudio/<module>/enum/<EnumName>.json` (where `module` defaults to `shared`)

Rules:
- Use 2-space indentation.
- Do not add fields outside the EnumConfig structure.
- If `readOnly=true`, do not overwrite an existing manifest file.

### 2) Java output path (exact)
Let `module = (resourceConfig.module ?? "shared").toLowerCase()`.

If `projectStructureStyle="technical"`:
- Output dir: `<mainPath>/constants`
- Output file: `<EnumName>.java`

If `projectStructureStyle="domain"`:
- Output dir: `<mainPath>/<module>/application/constants`
- Output file: `<EnumName>.java`

If `readOnly=true`, do not overwrite an existing Java file.

### 3) Java output content (byte-for-byte)
The enum Java must match the reference output byte-for-byte. Generate the file using this exact shape and formatting:

```java
/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME */

package <computed.package>;

import cv.igrp.framework.core.domain.IgrpEnum;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
<if projectStructureStyle="technical">
import <group>.<packageName>.exceptions.IgrpResponseStatusException;
<else>
import <group>.<packageName>.shared.domain.exceptions.IgrpResponseStatusException;
<end>

public enum <EnumName> implements IgrpEnum<String> {

  <ENUM_CONSTANTS>;

  <if hasAttributes>
  <for each attribute>
  private final <AttributeJavaType> <lowerAttributeName>;
  <end>

  <EnumName>(<AttributeCtorSignature>) {
    <for each attribute>
    this.<lowerAttributeName> = <lowerAttributeName>;
    <end>
  }
  <end>

  @Override
  public String getCode() {
    return code;
  }

  @Override
  public String getDescription() {
    return description;
  }

  /**
  * Pre-built maps for fast lookup.
  */
  private static final Map<String, <EnumName>> CODE_MAP = Arrays.stream(values())
          .collect(Collectors.toMap(<EnumName>::getCode, Function.identity()));

  /**
  * Attempts to find the enum value associated with the given code.
  * @param code The code to look up
  * @return An Optional containing the enum value if found, empty Optional otherwise
  */
  public static Optional<<EnumName>> fromCode(String code) {
    return Optional.ofNullable(CODE_MAP.get(code));
  }

  /**
  * Finds the enum value associated with the given code or throws an exception if not found.
  * @param code The code to look up
  * @return The enum value for the given code
  * @throws IllegalArgumentException if no enum value exists for the given code
  */
  public static <EnumName> fromCodeOrThrow(String code) {
    return fromCode(code).orElseThrow(() -> IgrpResponseStatusException.of(HttpStatus.BAD_REQUEST, "Invalid <EnumName> for this code: " + code));
  }

  /**
  * Returns a map of code to description.
  */
  public static Map<String, String> codeDescriptionMap() {
    return CODE_MAP.values().stream().collect(Collectors.toMap(<EnumName>::getCode, <EnumName>::getDescription));
  }

}
```

Rules to fill placeholders:
- `<computed.package>`: use the package derivation rules in paths.md (resolvePackage).
- `<ENUM_CONSTANTS>`: one constant per `values[]`, uppercased name, with optional `(<value.attributes...>)`, separated by commas, ending with `;` exactly like the shape above.
- `<AttributeJavaType>`: use the same type resolution as DTO (resolve-type) for each `EnumConfig.attributes[]` item.
- `<AttributeCtorSignature>`: comma-separated `<AttributeJavaType> <lowerAttributeName>` in the same order as `attributes[]`.

