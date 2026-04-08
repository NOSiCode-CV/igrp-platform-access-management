## Operation: newApi (BaseApiConfig)

### Input
A Base API manifest with `type: "springboot"` and required fields:
- `name`, `group`, `artifact`, `database`, `projectStructureStyle`, `enableObservability`, `enableEntityRevision`, `enableGraalVm`

### Schema validation (manifest structure)
The Base API manifest must be structurally valid.

Top-level allowed keys (no others):
- `id`, `workspaceId`, `version`, `type`, `name`, `group`, `artifact`, `database`, `description`, `package`, `projectStructureStyle`, `enableObservability`, `enableEntityRevision`, `javaVersion`, `springDocVersion`, `springCloudVersion`, `springBootVersion`, `dependencies`, `enableGraalVm`

Top-level required keys:
- `type`, `name`, `group`, `artifact`, `database`, `projectStructureStyle`, `enableObservability`, `enableEntityRevision`, `enableGraalVm`

Constraints:
- `type` must equal `springboot`.
- `name` must follow the naming convention: starts with a letter, then letters/digits/underscore only.
- `group` must be a dotted Java group string containing only letters/digits/dot/underscore (no spaces).
- `artifact` must contain only letters/digits/dot/underscore/hyphen (no spaces).
- `database` must be one of: `Postgresql`, `MySQL`, `H2`, `Oracle`.
- `enableObservability`, `enableEntityRevision`, `enableGraalVm` must be booleans.
- `description`, `package`, `javaVersion`, `springDocVersion`, `springCloudVersion`, `springBootVersion`, `id`, `workspaceId`, `version` are optional strings (when present).

`dependencies` (optional):
- array of objects; each item allowed keys (no others):
  - `name`, `groupId`, `artifactId`, `scope`, `version`, `bom`
- required keys per dependency:
  - `name`, `groupId`, `artifactId`, `scope`

### Output (complete project scaffold)
This operation generates a full Spring Boot project scaffold, including:
- `.igrpstudio/baseApi.json`
- project directories (technical vs domain)
- core Java bootstrap + shared configs (audit, security, exceptions, enum exposer, swagger)
- `src/main/resources` profile files and banner
- root config files (`pom.xml`, `Dockerfile`, etc.)
- `k8s/` base manifests
- `monitoring/` manifests (only when `enableObservability=true`)

### baseApi.json content rules (exact)
- Compute `packageName` from `artifact` (see paths.md).
- Write JSON using 2-space indentation.
- Add `package` as `<group>.<packageName>`.
- Do not invent extra fields outside BaseApiConfig/ApiConfig.

### Generated directory tree (diagram)
Below is the generated layout. Replace `<group-path>` and `<packageName>` using paths.md.

#### Technical style (`projectStructureStyle="technical"`)
```
<basePath>/
  .env
  .env.example
  pom.xml
  Dockerfile
  docker-compose.yml
  .editorconfig
  .gitignore
  .gitlab-ci.yml
  .dockerignore
  k8s/
    deployment.yaml
    ingress.yaml
    cluster.yaml
    service.yaml
  monitoring/                                  (only if enableObservability=true)
    collector/otel-collector.yml
    prometheus/prometheus.yml
    promtail/promtail-docker-config.yml
    tempo/tempo.yml
  .igrpstudio/
    baseApi.json
    shared/
      dto/.gitkeep
      models/.gitkeep
  src/
    main/
      java/<group-path>/<packageName>/
        <ApiName>Application.java
        config/
          AuditEntity.java
          SwaggerConfig.java
          IgrpEnumDynamicRestExposer.java
          ApplicationAuditorAware.java
        controllers/
        models/
        services/
        security/
          SecurityConfig.java
        exceptions/
          GlobalExceptionHandler.java
          IgrpResponseStatusException.java
      resources/
        application.properties
        application-development.properties
        application-staging.properties
        application-production.properties
        banner.txt
    test/
      java/<group-path>/<packageName>/
        repositories/
        services/
```

#### Domain style (`projectStructureStyle="domain"`)
```
<basePath>/
  .env
  .env.example
  pom.xml
  Dockerfile
  docker-compose.yml
  .editorconfig
  .gitignore
  .gitlab-ci.yml
  .dockerignore
  k8s/
    deployment.yaml
    ingress.yaml
    cluster.yaml
    service.yaml
  monitoring/                                  (only if enableObservability=true)
    collector/otel-collector.yml
    prometheus/prometheus.yml
    promtail/promtail-docker-config.yml
    tempo/tempo.yml
  .igrpstudio/
    baseApi.json
    shared/
      dto/.gitkeep
      models/.gitkeep
  src/
    main/
      java/<group-path>/<packageName>/
        <ApiName>Application.java
        shared/
          config/
            AuditEntity.java
            SwaggerConfig.java
            IgrpEnumDynamicRestExposer.java
            ApplicationAuditorAware.java
          security/
            SecurityConfig.java
          domain/
            events/
              EventPublisher.java
            models/.gitkeep
            repository/.gitkeep
            service/.gitkeep
            exceptions/
              GlobalExceptionHandler.java
              IgrpResponseStatusException.java
          infrastructure/
            messaging/
            persistence/
              entity/
              repository/
            spring/
              CommandBus.java
              QueryBus.java
          interfaces/
            rest/
      resources/
        application.properties
        application-development.properties
        application-staging.properties
        application-production.properties
        banner.txt
    test/
      java/<group-path>/<packageName>/shared/
        application/
          commands/
          queries/
          dto/
        domain/
          events/
          models/
          repository/
          service/
        infrastructure/
          controller/
          messaging/
          persistence/
            entity/
            repository/
```

### Generated files (inventory)
Always generated at `<basePath>/`:
- `.env`, `.env.example`, `pom.xml`, `Dockerfile`, `docker-compose.yml`, `.editorconfig`, `.gitignore`, `.dockerignore`
- CI file:
  - when `enableObservability=false`: `.gitlab-ci.yml`
  - when `enableObservability=true`: `.gitlab-ci.yaml`

Always generated under `k8s/`:
- `deployment.yaml`, `ingress.yaml`, `cluster.yaml`, `service.yaml`

Generated only when `enableObservability=true`:
- `monitoring/collector/otel-collector.yml`
- `monitoring/prometheus/prometheus.yml`
- `monitoring/promtail/promtail-docker-config.yml`
- `monitoring/tempo/tempo.yml`

### Key file contents (exact outputs)

#### <ApiName>Application.java
The application class name is the API `name` with the first letter uppercased, plus `Application`.
Write `<mainPath>/<ApiName>Application.java` with this exact content and blank lines:
```java
package <computed.package>;

<if domain>
import <group>.<packageName>.shared.config.ApplicationAuditorAware;
<else>
import <group>.<packageName>.config.ApplicationAuditorAware;
<end>
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditAware", dateTimeProviderRef = "auditDateTimeProvider")
public class <ApiName>Application {

  @Bean
  public AuditorAware<String> auditAware() {
    return new ApplicationAuditorAware();
  }

  @Bean
  public DateTimeProvider auditDateTimeProvider() {
    return () -> Optional.of(LocalDateTime.now());
  }

  public static void main(String[] args) {
    SpringApplication.run(<ApiName>Application.class, args);
  }
}
```

#### src/main/resources/application.properties
Write `src/main/resources/application.properties` exactly as below, selecting the datasource block based on `database`, and including the observability block only when `enableObservability=true`.

```properties
# ---------------------------------------------
# General Configuration
# ---------------------------------------------
spring.jpa.open-in-view=false
spring.application.name=<name>
spring.profiles.active=${SPRING_ACTIVE_PROFILE:development}
server.shutdown=graceful
management.endpoint.health.probes.enabled=true
# Properties to control the automatic rest endpoint creation for enum values
igrp.enum.exposer.enabled=${IGRP_ENUM_EXPOSER_ENABLED:true}
igrp.enum.exposer.path=${IGRP_ENUM_EXPOSER_PATH:api/enums}
# --------------------------------------------------------
# IGRP ACCESS BASE URL
# --------------------------------------------------------
igrp.access.api.base-url=${IGRP_ACCESS_API_BASE_URL}
# --------------------------------------------------------
# Datasource
# --------------------------------------------------------
<DATASOURCE_BLOCK>
# ----------------------------------------------------------------------------
# JPA and Hibernate settings
# ----------------------------------------------------------------------------
spring.jpa.hibernate.ddl-auto=${HIBERNATE_DDL:update}
spring.jpa.show-sql=${ENABLE_HIBERNATE_SQL:true}
spring.jpa.properties.hibernate.format_sql=${ENABLE_HIBERNATE_FORMAT_SQL:true}
spring.jpa.properties.hibernate.default_schema=${DB_SCHEMA:public}
<DIALECT_BLOCK>
<ENTITY_REVISION_BLOCK>
# ---------------------------------------------
# Spring Data Rest
# ---------------------------------------------
spring.data.rest.detection-strategy=annotated
# ---------------------------------------------
# Logging (common defaults)
# ---------------------------------------------
#sets the baseline logging level for the entire application
logging.level.root=${LOGGING_LEVEL_ROOT:INFO}
#targets the web layer, allowing control over HTTP request/response logs
logging.level.org.springframework.web=${LOGGING_LEVEL_SPRING_WEB:INFO}
# ---------------------------------------------
# Swagger Documentation
# ---------------------------------------------
springdoc.swagger-ui.enabled=${ENABLE_SWAGGER:true}
springdoc.swagger-ui.operationsSorter=method
springdoc.swagger-ui.tryItOutEnabled=true
springdoc.swagger-ui.docExpansion=none
<OBSERVABILITY_BLOCK>
```

Datasource blocks:

**Postgresql**
```properties
spring.datasource.url=jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DATABASE}
spring.datasource.username=${POSTGRES_USER}
spring.datasource.password=${POSTGRES_PASSWORD}
spring.datasource.driver-class-name=org.postgresql.Driver
```
Dialect block (Postgresql): empty.

**MySQL**
```properties
spring.datasource.url=jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE}
spring.datasource.username=${MYSQL_USER}
spring.datasource.password=${MYSQL_PASSWORD}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```
Dialect block (MySQL):
```properties
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect
```

**Oracle**
```properties
spring.datasource.url=jdbc:oracle:thin:@//${ORACLE_HOST}:${ORACLE_PORT}/${ORACLE_SERVICE_NAME}
spring.datasource.username=${ORACLE_USER}
spring.datasource.password=${ORACLE_PASSWORD}
spring.datasource.driver-class-name=oracle.jdbc.OracleDriver
```
Dialect block (Oracle):
```properties
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.Oracle12cDialect
```

**H2**
```properties
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=password
spring.sql.init.platform=h2
```
Dialect block (H2): empty.

Entity revision block (only when `enableEntityRevision=true`):
```properties
# Entity Revision
# Track entity names modified during a revision
#spring.jpa.properties.org.hibernate.envers.track_entities_changed_in_revision=true
# Enable withModifiedFlag globally
#spring.jpa.properties.org.hibernate.envers.global_with_modified_flag=true
# Set schema and catalog for audit tables
#spring.jpa.properties.org.hibernate.envers.default_schema=audit_schema
```

Observability block (only when `enableObservability=true`):
```properties
# Observability
otel.resource.attributes.service.name=${spring.application.name}
otel.resource.attributes.deployment.environment=${spring.profiles.active}
otel.logs.exporter=otlp
otel.metrics.exporter=otlp
otel.traces.exporter=otlp
otel.exporter.otlp.endpoint=${OTEL_COLLECTOR_ENDPOINT}
otel.sdk.disabled=${OTEL_DISABLED:true}
```

#### src/main/resources/application-development.properties
```properties
server.port=${SERVICE_PORT}
spring.config.import=optional:file:.env[.properties]

# Eureka Configurations
eureka.client.enabled=true
spring.cloud.refresh.enabled=false
eureka.client.service-url.defaultZone=http://igrp-eureka:8761/eureka/
eureka.client.register-with-eureka=true
eureka.client.fetch-registry=false
eureka.instance.prefer-ip-address=true

# Spring Cloud Kubernetes
spring.cloud.kubernetes.discovery.enabled=${KUBERNETES_DISCOVERY_ENABLED:false}

# Authentication
#spring.security.oauth2.resourceserver.jwt.issuer-uri=${AUTH_JWT_ISSUER}
auth.jwt.issuer=${AUTH_JWT_ISSUER}

logging.level.<group>.<artifact>=${LOGGING_LEVEL_APP:DEBUG}
```

#### src/main/resources/application-staging.properties
```properties
# Eureka Configurations
eureka.client.enabled=false

# Spring Cloud Kubernetes
spring.cloud.kubernetes.discovery.enabled=${KUBERNETES_DISCOVERY_ENABLED:true}

# Authentication
spring.security.oauth2.resourceserver.jwt.issuer-uri=${AUTH_JWT_ISSUER}

# Swagger
springdoc.swagger-ui.enabled=${ENABLE_SWAGGER:false}
```

#### src/main/resources/application-production.properties
```properties
# Eureka Configurations
eureka.client.enabled=false

# Spring Cloud Kubernetes
spring.cloud.kubernetes.discovery.enabled=${KUBERNETES_DISCOVERY_ENABLED:true}

# Authentication
spring.security.oauth2.resourceserver.jwt.issuer-uri=${AUTH_JWT_ISSUER}

# Swagger
springdoc.swagger-ui.enabled=${ENABLE_SWAGGER:false}
```

#### src/main/resources/banner.txt
Write the banner exactly as below:
```
                       ▓▓▓▓▓▓▓
                    ▓▓▓▓   ▓▓▓▓
                  ▓▓▓▓     ▓▓▓▓
                 ▓▓▓▓         ▓▓▓▓▓▓▓▓▓▓▓▓▓
                 ▓▓▓▓▓▓▓▓▓▓▓▓▓          ▓▓▓▓
            _                 ▓▓      ▓▓▓▓▓
           (_)                  ▓▓▓▓▓▓▓▓▓▓
            _    __ _   _ __   _ __
           | |  / _` | | '__| | '_ \
           | | | (_| | | |    | |_) |
           |_|  \__, | |_|    | .__/
                 __/ |        | |
                |___/         |_|

           IGRP 3.0 Server Application :: <baseVersion>
           Powered by Spring Boot ${spring-boot.version}
```
