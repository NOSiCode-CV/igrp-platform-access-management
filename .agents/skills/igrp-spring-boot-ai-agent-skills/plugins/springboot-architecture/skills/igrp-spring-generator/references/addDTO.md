## Operation: addDTO (DTOConfig)

### Table of contents
- Preconditions
- Schema validation (manifest structure)
- Manifest write (exact)
- Java output path (exact)
- Java output shapes (exact)
- Helper algorithms (must match reference)

### Preconditions
- `<basePath>/.igrpstudio/baseApi.json` must exist and define `group`, `packageName`, and `projectStructureStyle`.
- Apply paths and package rules from paths.md.

### Schema validation (manifest structure)
The DTO manifest must be structurally valid.

Top-level allowed keys (no others):
- `id`, `version`, `type`, `name`, `enableCustonValidation`, `readOnly`, `template`, `attributes`, `module`, `response`, `extends`

Top-level required keys:
- `type`, `template`, `name`, `attributes`

Constraints:
- `template` must be exactly `classic` or `record`.
- `name` and `module` (if present) must follow the naming convention: starts with a letter, then letters/digits/underscore only.

`extends` (optional) allowed keys (no others):
- `name`, `module`
Rules:
- when `extends` exists, both `name` and `module` are required.

`response` (optional):
- object (its internal keys are not constrained by this schema rule-set).

`attributes`:
- must be an array
- each item must be an object with only these allowed keys (no others):
  - `type`, `name`, `objectType`, `required`, `before`, `after`, `positive`, `maxLength`, `minLength`, `regex`, `isEmail`, `isUrl`, `collectionType`, `primaryKey`, `response`, `module`, `jsonAttributeName`, `xmlAttributeName`
- required keys per attribute:
  - `type`, `name`, `objectType`, `required`
Rules:
- `objectType` must be one of: `model`, `dto`, `java`, `enum`.
- `type`, `name`, and `module` (if present) must follow the naming convention: starts with a letter, then letters/digits/underscore only.
- `jsonAttributeName` and `xmlAttributeName` (if present) must contain only letters/digits/underscore (no spaces).

### 1) Manifest write (exact)
Normalize the DTO name exactly as:
- If `type="dto"`: remove a trailing `dto`/`DTO` suffix from `name` (only at the end).
- If `type="command"`: remove trailing `command` (case-insensitive).
- If `type="query"`: remove trailing `query` (case-insensitive).
- If `type="event"`: remove trailing `event` (case-insensitive).

Then write:
- `.igrpstudio/<module>/dto/<NormalizedName><Suffix>.json`
- Use 2-space indentation.
- Do not add extra fields not present in the DTOConfig structure.

### 2) Java output path (exact)
Let `module = (resourceConfig.module ?? "shared").toLowerCase()`.

If `projectStructureStyle="technical"`:
- Output dir: `<mainPath>/dto`
- File:
  - `type="dto"|"response"|"filter"` → `<NormalizedName>DTO.java`
  - `type="command"` → `<NormalizedName>Command.java`
  - `type="query"` → `<NormalizedName>Query.java`
  - `type="event"` → `<NormalizedName>Event.java`

If `projectStructureStyle="domain"`:
- Output dir:
  - `type="dto"|"response"|"filter"` → `<mainPath>/<module>/application/dto`
  - `type="command"` → `<mainPath>/<module>/application/commands`
  - `type="query"` → `<mainPath>/<module>/application/queries`
  - `type="event"` → `<mainPath>/<module>/domain/events`

### 3) Java DTO content (template-equivalent, exact)
The DTO Java output must match the following file shapes byte-for-byte.

#### A) Technical, template=classic, type=dto
```java
/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME */

package <computed.package>;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
<if extends>import lombok.EqualsAndHashCode;<end>
<if hasAttributes>
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
<end>
<if enableCustonValidation>
import <group>.<packageName>.dto.validator.<INameDTOValidator>;
<end>
<resolve-imports output, sorted>
<if hasAttributes>
@Data
@NoArgsConstructor
@AllArgsConstructor
<end>
<for each resourceConfig.annotations>@<annotation><end>
<if enableCustonValidation>@<INameDTOValidator><end>
<if extends>@EqualsAndHashCode(callSuper = true)<end>
@IgrpDTO
public class <Name>DTO <if extends>extends <ParentName>DTO<end> {

    <per attribute>
    <per attribute.annotations>@<annotation><end>
    <resolveAnnotations output>
    private <resolve-type> <fieldName> <collectionInitializer>;

    <end>
}
```

#### B) Technical, template=record, type=dto
```java
/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME */

package <computed.package>;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
<resolve-imports output, sorted>

<for each resourceConfig.annotations>@<annotation><end>
@IgrpDTO
public record <Capitalize(Name)>DTO(
    <per attribute>
    <per attribute.annotations>@<annotation><end>
    <resolveAnnotations output>
    <resolve-type> <fieldName><if last><else>,<end>
    <end>
){}
```

#### C) Domain, template=classic, type=dto
Same as Technical classic with these differences:
- The custom validator import uses:
  - `import <group>.<packageName>.<module>.application.dto.validator.<INameDTOValidator>;`
- Insert `@Valid` above fields where `attribute.objectType === "dto"`.
- Spacing/indentation must match exactly the reference template:
  - field block uses two spaces before annotations (not four).

#### D) Domain, template=record, type=dto
Same as Technical record, but uses the domain output directory and package, and no Lombok imports.

### 4) Helper algorithms (must match reference)
Implement these exactly when generating DTO Java.

#### Import resolution
Build a set of imports, then output them as:
- remove falsy entries
- sort lexicographically
- join with `\n`

Rules:
1) If `config.extends` exists and `extends.module != config.module`, import the parent DTO from the parent module package path (domain vs technical differs).
2) For each attribute:
   - If `attr.type != "object"`, try `GENERIC_IMPORTS(packageNameFromConfig, attr.type, attr.module).get(attr.objectType)` and add the domain/technical variant depending on style.
   - If `attr.type == "object"`, use `GENERIC_IMPORTS(packageNameFromConfig, attr.name, attr.module).get(attr.objectType)`.
   - If `GENERIC_TYPES.get(attr.type)?.java.namespace` exists, add `import <namespace>.<java.name>;`
   - If `GENERIC_IMPORTS(packageNameFromConfig, attr.type).get(attr.type)` exists, add the technical import.
3) If any attribute has `jsonAttributeName`, add the JsonProperty import.
4) If any attribute has `xmlAttributeName`, add the JacksonXmlProperty import.
5) If any attribute has `collectionType`, add the collection interface import plus initializer imports:
   - list/collection → `import java.util.ArrayList;`
   - set → `import java.util.HashSet;`
   - map → `import java.util.HashMap;`

#### Type resolution
1) If `attribute.type === "object"` and `attribute.objectType === "dto"`:
   - base type = attribute name with any trailing `DTO` removed, then append `DTO`
2) Else:
   - base type = `GENERIC_TYPES.get(attribute.type)?.java.name` if present
   - otherwise if `attribute.objectType === "dto"`: attribute `type` with any trailing `DTO` removed, then append `DTO`
   - otherwise base type is `attribute.type`
3) Wrap by `collectionType`:
   - `collection` → `Collection<base>`
   - `map` → `Map<base, ?>`
   - `pageable` → `Page<base>`
   - `list` → `List<base>`
   - `set` → `Set<base>`
   - otherwise → `base`

#### Validation annotation resolution
Generate a `\n\t`-joined string of annotations in this order:
1) `@JsonProperty("...")` if `jsonAttributeName`
2) `@JacksonXmlProperty(localName = "...")` if `xmlAttributeName`
3) Required validations (`required=true`):
   - if `type === "string"` and no collectionType or collectionType is `none`: `@NotBlank(message = "The field <name> is required")`
   - else `@NotNull(message = "The field <name> is required")`
   - if `collectionType === "list"` also `@NotEmpty(message = "The field <name> must not be empty")`
4) String validations: `@Size(min=...)`, `@Size(max=...)`, `@Pattern(...)` (escape backslashes)
5) Email/URL: `@Email(...)`, `@URL(...)`
