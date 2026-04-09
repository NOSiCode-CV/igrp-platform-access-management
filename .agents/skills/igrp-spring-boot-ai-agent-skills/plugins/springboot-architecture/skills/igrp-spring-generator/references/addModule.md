## Operation: addModule (ModuleConfig)

### Preconditions
- `<basePath>/.igrpstudio/baseApi.json` must exist and define `group`, `packageName`, and `projectStructureStyle`.
- Apply paths and package rules from paths.md.

### Schema validation (manifest structure)
The module manifest must be structurally valid.

Top-level allowed keys (no others):
- `type`, `name`

Top-level required keys:
- `type`, `name`

Constraints:
- `type` must equal `module`.
- `name` must follow the naming convention: starts with a letter, then letters/digits/underscore only.

### 1) Manifest write (exact)
Write the ModuleConfig JSON to:
- `.igrpstudio/<moduleName>/module.json`

Rules:
- Use 2-space indentation.
- Do not add fields outside the ModuleConfig structure.
- Normalize `<moduleName>`: replace `-` with `_`, remove non `[a-zA-Z0-9_]`, then lowercase.

### 2) Generated folder tree (diagram)
Below is what gets created (folders + `.gitkeep` files). Replace `<group-path>` and `<packageName>` using paths.md.

#### Technical style (`projectStructureStyle="technical"`)
```text
<basePath>/
  .igrpstudio/
    <moduleName>/
      module.json
      controllers/                 (directory created)
      models/
        .gitkeep
      dto/
        .gitkeep
  src/
    main/
      java/<group-path>/<packageName>/
        models/                    (directory created)
        services/                  (directory created)
        controllers/               (directory created)
        config/                    (directory created)
        security/                  (directory created)
```

#### Domain style (`projectStructureStyle="domain"`)
```text
<basePath>/
  .igrpstudio/
    <moduleName>/
      module.json
      models/
        .gitkeep
      dto/
        .gitkeep
  src/
    main/
      java/<group-path>/<packageName>/
        <moduleName>/
          application/
            dto/                   (directory created)
            commands/              (directory created)
            queries/               (directory created)
          domain/
            models/
              .gitkeep
            repository/
              .gitkeep
            service/
              .gitkeep
            events/
              .gitkeep
          infrastructure/
            messaging/             (directory created)
            persistence/
              entity/              (directory created)
              repository/          (directory created)
          interfaces/
            rest/                  (directory created)
```

### 3) .gitkeep output (exact)
Every `.gitkeep` file written by this operation must contain exactly:
```text
This file is used to let Git track empty directories.
```

Notes:
- In technical style, the module does not create a module-specific Java package tree. Java folders are created at the API root package, while the module is represented under `.igrpstudio/<moduleName>/`.
