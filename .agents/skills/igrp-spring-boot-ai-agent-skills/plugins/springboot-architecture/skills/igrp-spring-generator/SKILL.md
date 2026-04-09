---
name: "igrp-spring-generator"
description: "Creates a Spring Boot project (newApi) and generates Java + .igrpstudio from IGRP manifests. Invoke for 'create Spring Boot project', 'create REST API/endpoints/routes', or create controller/actions/DTO/model/enum/module."
---

# IGRP Spring Generator

## Goal
Emulate the reference generator behavior without the library: take IGRP Studio-compatible JSON manifests and generate:
- `.igrpstudio/**.json` manifests to the exact paths
- Spring Boot Java code byte-for-byte identical to the reference output (same content, imports, headers, formatting, and layout)

## Compatibility (non-negotiable)
- Never invent fields that are not part of the manifest type structure.
- Keep the JSON shape exactly compatible with the manifest schema (same keys, nesting, and file locations).
- Generate byte-for-byte identical Java when the reference rules exist; otherwise generate best-effort Java without changing manifest structure.
- When generating a manifest from intent or from Java, include all required fields and never add unknown fields.
- Treat the schema rules described in each operation reference as authoritative for manifest structure.

## How to use this skill
This skill supports three input modes:

### Mode A: Intent-only (no manifest provided)
If the user asks something like “create a DTO/controller/model/enum/module” or “initialize a new API” without providing a manifest:
1) Collect the required fields for the corresponding manifest type (ask the user for required fields, do not guess them).
2) Produce a valid manifest JSON (structure compatible, no extra fields).
3) Write the manifest to the correct `.igrpstudio/**.json` path.
4) Generate the corresponding Java output byte-for-byte like the reference generator.

### Mode B: Manifest → Java (manifest provided)
If the user provides a manifest and asks for generation:
1) Read `<basePath>/.igrpstudio/baseApi.json` when generating anything other than `newApi`.
2) Write the manifest JSON into `.igrpstudio/**` using the exact reference paths and naming.
3) Generate the Java file(s) in `src/main/java/**` (and test files if required) exactly like the reference output.

### Mode C: Java → Manifest (Java provided)
If the user provides Java code and asks to produce a manifest:
1) Infer what you can safely infer (names, fields, package/module hints).
2) Output a manifest JSON that is structurally valid for the target type.
3) Never invent fields outside the type structure; if information is missing, omit optional fields and require user input for required fields.
4) Save the manifest to the correct `.igrpstudio/**.json` path.

Always report:
- the list of files written (absolute paths)
- the final manifest JSON content
- short snippets for primary Java files

## Workflow (high level)
1) Identify the target project `basePath`.
2) Ensure `.igrpstudio/baseApi.json` exists before generating other elements.
3) Validate manifest structure against the schema rules of the target object.
4) Normalize names and paths exactly like the engine.
5) Write the `.igrpstudio/**.json` manifest.
6) Generate and write the corresponding Java files to the exact output paths.

## Supported operations
- `newApi` (BaseApiConfig)
- `addModule` (ModuleConfig)
- `addDTO` (DTOConfig)
- `addController` (ControllerConfig)
- `addModel` (ModelConfig) including relations (addModelWithRelation)
- `addEnum` (EnumConfig)

## References (progressive disclosure)
Keep this SKILL.md short. Read the following files only when you need their rules:
- [paths.md](./references/paths.md): naming normalization, manifest paths, Java base paths, and package derivation.
- [newApi.md](./references/newApi.md): byte-for-byte rules for initializing a project from BaseApiConfig.
- [addDTO.md](./references/addDTO.md): byte-for-byte rules for DTO manifests and Java generation (including helper algorithms).
- [addEnum.md](./references/addEnum.md): byte-for-byte rules for enum manifests and Java generation.
- [addModule.md](./references/addModule.md): byte-for-byte rules for module manifest and scaffolding.
- [addController.md](./references/addController.md): byte-for-byte rules for controller manifest and Java generation.
- [addModel.md](./references/addModel.md): byte-for-byte rules for model manifest, entity, relations, and repositories.

## Minimal examples

### Example: newApi
Input (BaseApiConfig):
```json
{
  "type": "springboot",
  "name": "demo",
  "group": "com.example",
  "artifact": "animals",
  "database": "Postgresql",
  "projectStructureStyle": "technical",
  "enableObservability": false,
  "enableEntityRevision": false,
  "enableGraalVm": false
}
```
Expected outputs (minimum for this phase):
- `<basePath>/.igrpstudio/baseApi.json`
- `<basePath>/src/main/java/com/example/animals/DemoApplication.java`
- `<basePath>/src/main/resources/application.properties`

### Example: addDTO
Input (DTOConfig):
```json
{
  "type": "dto",
  "module": "shared",
  "name": "UserDTO",
  "template": "classic",
  "attributes": [
    { "name": "id", "type": "integer", "required": true },
    { "name": "email", "type": "string", "isEmail": true }
  ]
}
```
Expected outputs:
- `<basePath>/.igrpstudio/shared/dto/UserDTO.json`
- `<basePath>/src/main/java/<group-path>/<packageName>/dto/UserDTO.java` (technical)

## Suggested trigger test prompts
Use these prompts to verify the skill triggers and follows the workflow:
1) "Initialize a new API project from this BaseApiConfig JSON and write the generated files."
2) "Create a DTO from this DTOConfig JSON and save both the .igrpstudio manifest and the Java file."
3) "I have an IGRP Studio manifest for a DTO. Generate the Spring Boot Java code and the .igrpstudio JSON."
