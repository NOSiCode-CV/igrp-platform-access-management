## Paths and naming rules (reference)

### normalizePackageName(artifact)
- Replace `-` with `_`
- Remove all characters outside `[a-zA-Z0-9_]`

### Java output base
Given `group` and computed `packageName`:
- `<group-path>` = `group` with `.` replaced by `/`
- `<mainPath>` = `src/main/java/<group-path>/<packageName>`
- `<testPath>` = `src/test/java/<group-path>/<packageName>`

### Java package statement (resolvePackage)
The Java `package ...;` line must be computed from the file output directory:
1) Convert paths to forward slashes.
2) Remove the `<basePath>/` prefix from the full output path.
3) Split by `/`, drop segments `src`, `main`, `test`, `java`.
4) Join the remaining segments with `.`.

### Manifest paths (.igrpstudio)
All manifests must be written under `<basePath>/.igrpstudio`:
- Base API: `.igrpstudio/baseApi.json`
- Module: `.igrpstudio/<module>/module.json`
- DTO: `.igrpstudio/<module>/dto/<NormalizedName><Suffix>.json`
- Controller: `.igrpstudio/<module>/controllers/<NormalizedName>Controller.json`
- Model: `.igrpstudio/<module>/models/<ModelName>.json`
- Enum: `.igrpstudio/<module>/enum/<EnumName>.json`

For DTO filename suffix:
- `type in ["dto","response","filter"]` → `DTO`
- `type="command"` → `Command`
- `type="query"` → `Query`
- `type="event"` → `Event`

