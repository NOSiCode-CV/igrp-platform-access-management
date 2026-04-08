## Operation: addController (ControllerConfig)

### Preconditions
- `<basePath>/.igrpstudio/baseApi.json` must exist and define `group`, `packageName`, and `projectStructureStyle`.
- Apply paths and package rules from paths.md.

### Schema validation (manifest structure)
The controller manifest must be structurally valid.

Top-level allowed keys (no others):
- `id`, `version`, `type`, `name`, `module`, `globalPermission`, `globalRoles`, `basePath`, `description`, `actions`

Top-level required keys:
- `type`, `name`, `basePath`, `actions`

Constraints:
- `type` must equal `controller`.
- `name` and `module` must follow the naming convention: starts with a letter, then letters/digits/underscore only.
- `basePath` must be a valid path string (letters/digits and `/` only, no spaces).
- `description` is optional, but if present it must be a non-null string.

`globalPermission` (optional) allowed keys (no others):
- `items`, `operator`
Rules:
- `items` is required when `globalPermission` exists and must be a non-empty array of strings.
- `operator` is optional; if present must be `AND` or `OR`.

`globalRoles` (optional):
- array of strings

`actions`:
- must be an array
- each item must be an object with only these allowed keys (no others):
  - `actionName`, `method`, `path`, `permission`, `roles`, `modelAttribute`, `requestParams`, `pathVariables`, `headers`, `multipartFiles`, `permissions`, `requestBody`, `responses`
- required keys per action:
  - `actionName`, `method`
Rules:
- `method` must be one of the supported HTTP methods (e.g. `GET`, `POST`, `PUT`, `PATCH`, `DELETE`).
- `path` is optional; if present it must follow the path naming convention (no spaces/special chars).
- `roles` (optional) is an array of strings.
- `permissions` (optional) is an array of strings.

`permission` (optional, singular) allowed keys (no others):
- `items`, `operator`
Rules:
- `items` is required when `permission` exists and must be a non-empty array of strings.
- `operator` is optional; if present must be `AND` or `OR`.

`modelAttribute` (optional) allowed keys (no others):
- `name`, `module`
Rules:
- `name` is required.
- `module` is optional.

`requestParams`, `pathVariables`, `multipartFiles` (optional):
- arrays of objects; each item allowed keys (no others):
  - `type`, `name`, `value`, `description`, `isRequired`
- required keys per item:
  - `type`, `name`, `isRequired`

`headers` (optional):
- array of objects; each item allowed keys (no others):
  - `type`, `header`, `value`, `isRequired`
- required keys per item:
  - `header`, `value`, `isRequired`

`requestBody` (optional) allowed keys (no others):
- `id`, `version`, `name`, `content`
Rules:
- `content` is required when `requestBody` exists and must be an object mapping content-types (e.g. `application/json`) to objects.

`requestBody.content` (required when `requestBody` exists):
- allowed keys: any string (content-type)
- each value must be an object (schema validation does not strictly constrain the internal keys of this object)

`responses` (optional):
- object mapping 3-digit status codes (e.g. `200`, `404`) to response bodies.
Each response body allowed keys (no others):
- `id`, `version`, `name`, `module`, `description`, `content`
Rules:
- `content` is required and must be an object mapping content-types to objects.

`responses.<status>.content` (required for each response body):
- allowed keys: any string (content-type)
- each value must be an object (schema validation does not strictly constrain the internal keys of this object)

Recommended content schema object (best-practice, matches generator conventions):
- when you include a schema definition inside a content-type object, use only these keys (no others):
  - `schema`
- when you include `schema`, use only these keys (no others):
  - `type`, `module`, `objectType`, `required`, `identifier`, `description`, `example`, `deprecated`, `items`, `properties`, `collectionType`
- `items` (optional) is a nested schema object and follows the same allowed keys, with `type` required inside `items` when present.
- `properties` (optional) is an object; keys are arbitrary property names; values are objects (schema validation allows free-form objects here).

### 1) Manifest write (exact)
Write the ControllerConfig JSON to:
- `.igrpstudio/<module>/controllers/<ControllerName>Controller.json` (where `module` defaults to `shared`)

Rules:
- Use 2-space indentation.
- Do not add fields outside the ControllerConfig structure.
- If `readOnly=true` exists in the structure for this type in the future, respect it (do not overwrite).

### 2) Java output paths (exact)
Let `module = (resourceConfig.module ?? "shared").toLowerCase()`.
Derive `ControllerName` from `resourceConfig.name` using Java class naming (PascalCase).
Derive `controllerNameLowerCamel` by lowercasing only the first character of `ControllerName`.
Derive `ActionName` from `actionName` using Java class naming (PascalCase).

If `projectStructureStyle="technical"`:
- Controller:
  - Output dir: `<mainPath>/controllers/<resourceConfig.name.toLowerCase()>`
  - File: `<ControllerName>Controller.java`
- Service interface:
  - Output dir: `<mainPath>/services`
  - File: `I<ControllerName>Service.java`
- Service implementation:
  - Output dir: `<mainPath>/services`
  - File: `<ControllerName>Service.java`

If `projectStructureStyle="domain"`:
- Controller:
  - Output dir: `<mainPath>/<module>/interfaces/rest`
  - File: `<ControllerName>Controller.java`
- For each action:
  - If method is `GET`:
    - Query: `<mainPath>/<module>/application/queries/<ActionName>Query.java`
    - Query handler: `<mainPath>/<module>/application/queries/<ActionName>QueryHandler.java`
    - Query handler test: `<testPath>/<module>/application/queries/<ActionName>QueryHandlerTest.java`
  - Else:
    - Command: `<mainPath>/<module>/application/commands/<ActionName>Command.java`
    - Command handler: `<mainPath>/<module>/application/commands/<ActionName>CommandHandler.java`
    - Command handler test: `<testPath>/<module>/application/commands/<ActionName>CommandHandlerTest.java`

### 3) Controller Java output (byte-for-byte)
Generate the controller Java with this exact structure and formatting (placeholders must be filled deterministically; do not introduce extra blank lines):

```java
/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME */

package <computed.package>;

<IMPORTS_BLOCK>

@IgrpController
@RestController
@RequestMapping(path = "<basePath>")
<if hasGlobalAuth>
@PreAuthorize("<GLOBAL_PREAUTHORIZE_EXPRESSION>")
<end>
@Tag(
    name = "<TagName>",
    description = "<ControllerDescription>"
)
public class <ControllerName>Controller {

<INJECTION_FIELDS>

<CONSTRUCTOR_BLOCK>

<for each action>
  <if hasActionAuth>
  @PreAuthorize("<ACTION_PREAUTHORIZE_EXPRESSION>")
  <end>
  @<HttpMethod>Mapping(<MAPPING_ARGS>)
  <OPERATION_ANNOTATION_BLOCK>
  public ResponseEntity<<ResolvedResponseType>> <actionMethodName>(<PARAMS>) {
<BODY_BLOCK>
  }

<end>
}
```

#### 3.1) Imports block
Imports must include the fixed set:
- `cv.igrp.framework.stereotype.IgrpController`
- `org.springframework.http.ResponseEntity`
- `org.springframework.web.bind.annotation.*`
- `org.springframework.http.HttpStatus`
- Swagger annotations (`io.swagger.v3...`)
- `jakarta.validation.Valid`
- `org.springframework.security.access.prepost.PreAuthorize`

Domain-only (depending on actions):
- If any GET action exists: `cv.igrp.framework.core.domain.QueryBus` and `...application.queries.*`
- If any non-GET action exists: `cv.igrp.framework.core.domain.CommandBus` and `...application.commands.*`

Plus any DTO/model/enum imports required by request bodies, responses, pageable, etc. Import ordering must match the reference output (lexicographic ordering after fixed blocks).

#### 3.2) Injection fields and constructor
Technical:
- `private final I<ControllerName>Service <controllerNameLowerCamel>Service;`
- Constructor:
```java
public <ControllerName>Controller(I<ControllerName>Service <controllerNameLowerCamel>Service){
  this.<controllerNameLowerCamel>Service = <controllerNameLowerCamel>Service;
}
```

Domain:
- If any GET action exists: `private final QueryBus queryBus;`
- If any non-GET action exists: `private final CommandBus commandBus;`
- Constructor includes only the buses that exist, in this order: `QueryBus queryBus, CommandBus commandBus`.

#### 3.3) ResolvedResponseType
The return type inside `ResponseEntity<...>` is derived from `responses`:
- If `responses` is missing or has more than 1 status code: use `?`
- Else:
  - Let `body = the single Body`
  - Let `schema = body.content["application/json"]?.schema ?? body.content["multipart/form-data"]?.schema`
  - If `schema.type === "object"`: base type is:
    - take `body.name` (or empty string if missing)
    - remove a trailing `dto` suffix (case-insensitive)
    - append `DTO`
    - apply Java class naming (PascalCase)
  - Else if `schema.objectType === "dto"`: base type is:
    - take `schema.type`
    - remove a trailing `dto` suffix (case-insensitive)
    - append `DTO`
    - apply Java class naming (PascalCase)
  - Else: base type is the Java primitive/wrapper mapping for `schema.type` (for `binary` keep as-is)
  - Wrap base type by `schema.collectionType`:
    - `collection` → `Collection<T>`
    - `map` → `Map<T, ?>`
    - `pageable` → `Page<T>`
    - `list` → `List<T>`
    - `set` → `Set<T>`
    - otherwise `T`

#### 3.4) Method parameters
Parameters are built from `headers`, `modelAttribute`, `requestBody`, `requestParams`, `pathVariables`, `multipartFiles`, and pageable:
- Non-Content headers become `@RequestHeader(...) String <sanitizedName>`
- requestBody:
  - if content is form-data: `@ModelAttribute <Type> <name>`
  - else: `@Valid @RequestBody <Type> <name>` when required by schema
- requestParams: `@RequestParam(value="...", required=..., defaultValue=...) <ResolvedType> <name>`
- pathVariables: `@PathVariable(value="...", required=...) <ResolvedType> <name>`
- multipartFiles: `@RequestPart("...") MultipartFile <name>`
- pageable: `@ParameterObject Pageable pageable` when the response is pageable

#### 3.5) Method body
Technical:
```java
    var response = <controllerNameLowerCamel>Service.<actionMethodName>(<args>);

      return response;
```

Domain:
- GET:
```java
      final var query = new <ActionName>Query(<args>);

      return queryBus.handle(query);
```
- Non-GET:
```java
      final var command = new <ActionName>Command(<args>);

      return commandBus.send(command);
```
