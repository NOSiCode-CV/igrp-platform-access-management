## Operation: addModel (ModelConfig) including relations

### Preconditions
- `<basePath>/.igrpstudio/baseApi.json` must exist and define `group`, `packageName`, and `projectStructureStyle`.
- Apply paths and package rules from paths.md.

### Schema validation (manifest structure)
The model manifest must be structurally valid.

Top-level allowed keys (no others):
- `id`, `version`, `type`, `name`, `tableName`, `primaryKey`, `attributes`, `crud`, `uniqueConstraints`, `relationReference`, `indexes`, `audit`, `revision`, `module`, `readOnly`

Top-level required keys:
- `type`, `name`, `tableName`, `attributes`

Constraints:
- `type` must equal `model`.
- `name`, `tableName`, and `module` (if present) must follow the naming convention: starts with a letter, then letters/digits/underscore only.

`primaryKey` (optional):
- array of objects; each item allowed keys (no others):
  - `type`, `name`, `length`, `defaultValue`
- required keys per item:
  - `type`, `name`

`attributes`:
- must be an array
- each item must be an object with only these allowed keys (no others):
  - `type`, `name`, `length`, `unique`, `skipFieldRevision`, `nullable`, `defaultValue`, `generationType`, `sequenceName`, `primaryKey`, `objectType`, `relation`, `module`
- required keys per attribute:
  - `type`, `name`

`relation` (optional, inside an attribute):
- allowed keys (no others):
  - `type`, `fetchType`, `cascadeType`, `orphanRemoval`, `entity`, `fieldName`, `mappedBy`, `referencedColumnName`, `cardinality`, `joinTable`, `inverseJoinColumn`, `joinColumn`, `module`
- required keys when `relation` exists:
  - `type`, `entity`
- `cascadeType` (optional) is an array of objects; each item allowed keys (no others):
  - `type`

`relationReference` (optional):
- array of objects; each item allowed keys (no others):
  - `type`, `fetchType`, `module`, `entity`, `fieldName`, `joinColumn`, `mappedBy`, `cascadeType`, `orphanRemoval`
- required keys per item:
  - `type`, `entity`

`uniqueConstraints` (optional):
- array of objects; each item allowed keys (no others):
  - `name`, `columns`
- required keys per item:
  - `name`, `columns`

`indexes` (optional):
- array of objects; each item allowed keys (no others):
  - `name`, `columns`, `unique`
- required keys per item:
  - `name`, `columns`, `unique`

### 1) Manifest write (exact)
Write the ModelConfig JSON to:
- `.igrpstudio/<module>/models/<ModelName>.json` (where `module` defaults to `shared`)

Rules:
- Use 2-space indentation.
- Do not add fields outside the ModelConfig structure.
- Ensure the manifest includes the required fields: `type`, `name`, `tableName`, `attributes`.

### 2) Java output paths (exact)
Let `module = (resourceConfig.module ?? "shared").toLowerCase()`.
Derive `ModelName` from `resourceConfig.name` using Java class naming (PascalCase).
If the provided name already ends with `Entity`, keep it. Model names are expected to include the `Entity` suffix (example: `UserEntity`).

If `projectStructureStyle="technical"`:
- Entity:
  - Output dir: `<mainPath>/models/<resourceConfig.name.toLowerCase()>`
  - File: `<ModelName>.java`
- Repository:
  - Output dir: `<mainPath>/models/<resourceConfig.name.toLowerCase()>`
  - File: `<ModelName>Repository.java`

If `projectStructureStyle="domain"`:
- Entity:
  - Output dir: `<mainPath>/<module>/infrastructure/persistence/entity`
  - File: `<ModelName>.java`
- Repository interface:
  - Output dir: `<mainPath>/<module>/domain/repository`
  - File: `<ModelName>Repository.java`
- Repository implementation:
  - Output dir: `<mainPath>/<module>/infrastructure/persistence/repository`
  - File: `<ModelName>RepositoryImpl.java`
- Persistence repository adapter:
  - Output dir: `<mainPath>/<module>/infrastructure/persistence/repository`
  - File: `<ModelName>EntityRepository.java`

### 3) Entity Java output (byte-for-byte)
Generate the entity Java with the exact structure below. Fill placeholders deterministically and do not add extra whitespace:

```java
/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME */

package <computed.package>;

<IMPORTS_BLOCK>

<if revision>@Audited<end>
@Getter
@Setter
@IgrpEntity
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "<tableName>"<UNIQUE_CONSTRAINTS><INDEXES>)
public class <ModelName> <if audit>extends AuditEntity<end> {

  <if hasEmbeddedPrimaryKey>
  @EmbeddedId
  private <ModelName>PrimaryKey id;
  <end>

  <for each attribute>
  <ATTRIBUTE_BLOCK>
  <end>

  <for each relationReference>
  <RELATION_REFERENCE_BLOCK>
  <end>
}
```

#### 3.1) Imports block
Imports depend on the model features:
- Always: JPA, Lombok annotations, and `cv.igrp.framework.stereotype.IgrpEntity`
- If `audit=true`: import `AuditEntity` from:
  - technical: `<group>.<packageName>.config.AuditEntity`
  - domain: `<group>.<packageName>.shared.config.AuditEntity`
- If `revision=true`: import `org.hibernate.envers.Audited` and optionally `NotAudited`
- If any `@ColumnDefault`: import Hibernate annotations
- If any `file` field: import `@JdbcType` and `BinaryJdbcType`
- If any relations: import `FetchType`, relationship annotations, plus collections (List/Set, ArrayList/HashSet)
- If any enums: import `@Enumerated` and `EnumType`

Import ordering must match the reference output.

#### 3.2) Attribute block rules (exact patterns)
For each `attributes[]` item, produce one of these blocks:

**A) Primary key attribute (`primaryKey=true`)**
- Must include `@Id`
- If `generationType` is not `"NONE"`, include `@GeneratedValue(...)` and `@SequenceGenerator(...)` when `SEQUENCE` with `sequenceName`
- Always include:
```java
@Column(name = "<lower(name)>", unique = true, nullable = false)
private <ResolvedType> <camelName>;
```

**B) Normal column (non-relation, non-text, non-file)**
- If `nullable=false`:
  - `string` or `text` → `@NotBlank(message = "<camelName> is mandatory")`
  - else → `@NotNull(message = "<camelName> is mandatory")`
- If `objectType="enum"`: `@Enumerated(EnumType.STRING)`
- `@Column(name="...", unique=..., nullable=..., length=...)` exactly as reference formatting
- Field type:
  - `file` → `byte[]`
  - otherwise resolve the Java type using the same rules described in addDTO.md (type/objectType + collection wrapping)

**C) Text column (`type="text"`)**
- Optionally `@ColumnDefault("'...'" )` when `defaultValue` exists
- `@Lob`
- `@Column(..., columnDefinition="TEXT")`
- Field is `String`

**D) File column (`type="file"`)**
- `@Lob`
- `@JdbcType(BinaryJdbcType.class)`
- `@Column(..., columnDefinition = "BLOB")`
- Field is `byte[]`

**E) Relation attribute (`type="relation"` with `relation`)**
Generate exactly one of:
- `@OneToOne` + `@JoinColumn(...)` + field `<EntityType> <fieldName>`
- `@OneToMany(mappedBy=..., ...)` + `List<...> ... = new ArrayList<>()`
- `@ManyToOne(...)` + `@JoinColumn(...)`
- `@ManyToMany(...)` + optional `@JoinTable(...)` + `Set<...> ... = new HashSet<>()`

Cascade, orphanRemoval, fetchType, joinColumn, referencedColumnName, joinTable, inverseJoinColumn, mappedBy must be taken from `relation`.

#### 3.3) relationReference blocks
For each entry in `relationReference[]`, generate the inverse side block:
- OneToOne: `@OneToOne(mappedBy=...) private <entity> <fieldName>;`
- ManyToOne inverse: `@OneToMany(mappedBy=...) private List<<entity>> ... = new ArrayList<>();`
- ManyToMany inverse: `@ManyToMany(mappedBy=..., ...) private Set<<entity>> ... = new HashSet<>();`

### 4) Repository output (byte-for-byte)
Technical:
- Generate `<ModelName>Repository.java` as a Spring Data repository interface in the same directory as the entity.

Domain:
- Generate repository interface in the domain layer and an implementation in infrastructure layer, plus the persistence adapter interface.

All repository files must match the reference output formatting and naming.
