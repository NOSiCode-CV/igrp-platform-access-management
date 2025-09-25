# Feature Specification: Multi-Provider IAM Synchronization API

---
## Revision

| Version | Author            | Date       | Changes                                    |
|---------|-------------------|------------|--------------------------------------------|
| 1.0.0   | @Marcelo.Monteiro | 2025-09-16 | Initial documentation                      |
| 1.0.1   | @Marcelo.Monteiro | 2025-09-22 | First revision changes                     |
| 1.0.2   | @Marcelo.Monteiro | 2025-09-23 | Add provider configuration standardization |
| ...     | ...               | ...        | ...                                        |

---

## Table of Contents
1. Overview
2. Goals & Non-Goals
3. Requirements
    - Functional Requirements
    - Non-functional Requirements
4. Constraints & Assumptions
5. Concepts & Definitions
6. High-Level Architecture/
7. Adapter Contract (`IAdapter`) — Detailed Interface
    - Capabilities & Semantics
    - Error Model & Retries
8. Sync Patterns & Algorithms
    - Event-driven sync (preferred)
    - Startup reconciliation (mandatory)
    - Conflict detection & resolution policies
9. Soft delete & lifecycle management
10. Federated/social login flows (Google example)
11. Mapping & transformation rules
12. Provider Configuration Standardization
13. Security considerations
14. Testing strategy and test cases (unit, integration, e2e, performance)
15. Migration & rollout plan

---

## 1. Overview

This specification details a robust synchronization system to keep one or more IAM providers (Keycloak, WSO2, and social providers surfaced through a primary IAM like Keycloak) consistent with a central Business Logic Database (DB). The DB is the canonical source of business data (users, roles, permissions, departments, applications, resources). Providers are authoritative for provider-specific attributes (password hashes, provider-managed MFA, social federations) but must converge with DB for business configuration.

The sync system must:
- Operate unidirectionally (DB → provider).
- Run a verification and reconciliation at API startup and on-demand.
- Be performant and scalable for large user bases.
- Be idempotent, resilient to retries and partial failures.
- Provide audit trails and reconciliation reports.

---

## 2. Goals & Non-Goals

### Goals
- Ensure eventual consistency with startup verification to speed up convergence.
- Minimize a provider load using delta checks and batched updates.
- Expose on-demand APIs for immediate checks and repairs.
- Maintain full auditability using Envers.

### Non-Goals
- Provide real-time strict consistency across globally distributed databases (we allow eventual consistency).
- Replace provider-native authentication flows or features (e.g., provider-managed MFA remains provider-owned).
- Implement UI in this specification — operational APIs and data models only.

---

## 3. Requirements

### Functional Requirements
1. Unidirectional synchronization between DB and the active provider.
2. Startup check: on API boot, verify DB vs. provider and repair differences.
3. On-demand API endpoints: `/sync/check`, `/sync/repair`, `/sync/status`.
4. Support multiple provider adapters implementing a shared `IAdapter`.
5. Provider-originated events must be consumable and able to create or update DB records (e.g., social user created).
6. Soft-delete semantics for lifecycle management.

### Non-functional Requirements
1. Scalable to millions of users and hundreds of thousands of roles/resources.
2. Low latency applies for per-request synchronous operations (admin flows).
3. Resilient to provider rate-limiting and partial outages.
4. Secure: credentials encrypted, the least privilege, audit trails.

---

## 4. Constraints & Assumptions
- Providers expose stable admin APIs to list, create, update, and delete objects.
- Providers may support bulk operations (optimal) but may also impose rate-limits.
- The DB supports reliable transactions, triggers, and scheduled background jobs.
- Spring Boot stack with JPA/Hibernate in the codebase.
- The system can add new DB columns and tables and run migrations.

---

## 5. Concepts & Definitions

- **SoT**: Source of Truth (the DB).
- **Adapter**: Provider-specific implementation of the `IAdapter` contract.
- **Reconciliation**: Process of comparing a DB data to a provider data and repairing differences.

---

## 6. High-Level Architecture

```
+---------------------+            +------------------+         +-------------------+
| Business DB (SoT)   | <-runner-> |     IAdapter     | <-API-> | Admin / Sync API  |
|   (JPA + Envers)    |            |                  |         | (/sync/check,...) |
+----------+----------+            +---------+--------+         +---------+---------+
           |                                 |                            |
           |                                 v                            |
           |                          +------+-------+                    |
           |                          | Provider SDK | ------------>  Providers
           |                          +------+-------+                   |
           |                                 |                            |
           +---------------------------------+----------------------------+
```

Components:
- **Business DB**: Entities.
- **Sync Core**:
    - Reconciler: startup and periodic reconciliation calls adapters.
    - Conflict resolver: DB wins.
- **Adapters**: KeycloakAdapter, WSO2Adapter, etc.
- **Admin API**: allows manual checks and repairs.

---

## 7. Adapter Contract (`IAdapter`) — Detailed Interface

Adapters must encapsulate provider-specific semantics and expose a uniform contract. Use Java interface signatures as examples.

```java
public interface IAdapter {

    /**
     * Provider identifier (e.g., "keycloak")
     */
    String getProviderName();

    /**
     * Health check for provider connectivity check.
     */
    AdapterHealth checkHealth();

    // keep the other methods the same
   
}
```

Adapters must:
- Normalize user provider responses into `UserIdentity`.
- Implement retries with exponential backoff and respect rate limits.

### 7.1 Error Model
- Throw `IAMException` containing:
    - `errorCode` (e.g., RATE_LIMIT, AUTH_FAIL, NOT_FOUND)
    - `retryable` boolean
    - `details`
- Sync core uses `retryable` to decide requeue/backoff.

---

## 8. Sync Patterns & Algorithms

This section defines the algorithms for efficient, resilient synchronization.

### 8.1 Event-driven Sync (Recommended)
- When an entity in DB is created/updated/deleted:
    - In the same DB transaction:
        - Calls adapter upsert/delete for relevant providers.
        - In case of conflicts (409 HTTP Status code), log and skip.
        - In case of errors (5xx, network), log the error and finish the processing.

Implementation details:
- This processor runs as a separate Spring Boot worker (or as part of the main app with a dedicated thread pool).

### 8.2 Startup Reconciliation (Mandatory)
- On API start (or periodic schedule), run the reconciler:
    - In the same DB transaction:
        - Call adapter and fetch data lists.
        - Compare with DB data.
        - For differences, call adapter upsert/delete as needed.

#### 8.2.1 Ordering & Dependencies
- Always sync definitions before assignments:
    - Roles & Permissions → Departments/Groups → Applications/Resources → Role→Permission assignments → User creation → User role/group assignments
- This avoids transient failures when creating users with roles referencing missing roles.

### 8.3 Conflict Detection & Resolution
- Decision: DB always wins by default.

---

## 9. Soft Delete & Lifecycle Management

### 9.1 Soft Delete Semantics
- Entities in DB are "deleted" by setting `updated_at` and `status` as DELETED.
- On delete:
    - Adapter removes the provider object.

### 9.2 Undo Delete
- Use Envers to roll back or admin UI to undelete: adapter re-creates provider objects.

---

## 10. Federated / Social Login Flow (Google Example)

This section explains the canonical flow and edge cases.

### 10.1 Goals
- When a user logs in via Google (federated through Keycloak or direct), ensure a DB user exists and is linked to the provider.
- Avoid unwanted account merging or takeover.
- If the user does not exist in DB, that means the user wasn't invited yet, and it will not be able to access the platform.

### 10.2 Data to store on user tables by invite feature
- `external_id` (`google:` Google `sub` or Keycloak federation link) (format: `provider:sub`)
- `email`, `email_verified` (copy)

### 10.3 Account Merge & Collision Handling
- If a provider `external_id` appears with email matching an existing `t_user` but `email_verified=false`, do **not auto-merge**. Instead, request user confirmation via email.
- Provide admin endpoints to manually link accounts, with audit trail.

### 10.4 Security considerations for federated creation
- Treat `email_verified` as authoritative only when coming from trusted provider and validated by Keycloak (i.e., Keycloak's `email_verified` is reliable).
- Avoid auto-linking if the risk of takeover exists (email is not unique, temporary emails, etc.).
- Keep an approval flow for sensitive roles assignment post-creation.

---

## 11. Mapping & Transformation Rules

Different providers have different models. Provide a mapping layer to transform a canonical DB model to provider-specific constructs.

### 11.1 Examples
- Canonical `Role` → Keycloak realm role or client role depending on `role.scope`.
- Canonical `Department` (code `DEPT_GPT`) → Keycloak group with name `DEPT_GPT` and attribute `business_code=DEPT_GPT`.
- Canonical `Permission` → provider-specific permission or scope. If the provider lacks fine-grained permissions, emulate using roles.

## 12. IAM Provider Configuration Standardization Analysis

### Current State Analysis

#### Existing Configuration Approach
```properties
# Provider-specific configuration (Keycloak example)
igrp.keycloak.server-url=...
igrp.keycloak.realm=igrp
igrp.keycloak.client-id=access-management
igrp.keycloak.client-secret=*****
igrp.keycloak.grant-type=client_credentials
```

#### Problems with Current Approach
1. **Provider-specific configurations** require different property structures
2. **Multiple application images** needed for different providers
3. **Tight coupling** between configuration and provider implementation
4. **No standardized interface** for OAuth2-based IAM providers

### Proposed Solution: Spring DataSource-like Configuration

#### Target Configuration Structure
```properties
# Standardized OAuth2 configuration
igrp.iam.provider=keycloak
igrp.iam.server-url=http://localhost:8080
igrp.iam.realm=igrp
igrp.iam.client-id=access-management
igrp.iam.client-secret=*****
igrp.iam.grant-type=client_credentials
igrp.iam.scope=openid profile email

# Provider-specific extensions (optional)
igrp.iam.keycloak.admin-client-id=admin-cli
igrp.iam.wso2.tenant=carbon.super
```

### Implementation Strategy

#### 1. Configuration Properties Standardization

_Core IAM Properties Class_
```java
@ConfigurationProperties(prefix = "igrp.iam")
public class IAMProperties {
    private String provider;
    private String serverUrl;
    private String realm;
    private String clientId;
    private String clientSecret;
    private String grantType = "client_credentials";
    private String scope = "openid profile email";
    
    // Provider-specific extensions
    private Map<String, String> extensions = new HashMap<>();
}
```

#### 2. Auto-Configuration Mechanism

_Spring Boot Auto-Configuration Pattern_
```java
@Configuration
@ConditionalOnClass(IAdapter.class)
@EnableConfigurationProperties(IAMProperties.class)
public class IAMAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public IAdapter iamAdapter(IAMProperties properties) {
        return IAMAdapterFactory.createAdapter(properties);
    }
}
```

#### 3. Provider Discovery and Adapter Factory

_Adapter Factory Implementation_
```java
package cv.igrp.framework.auth;

import cv.igrp.framework.auth.core.IAdapter;
import cv.igrp.framework.auth.core.IAMProperties;

public class IAMAdapterFactory {

    public static IAdapter createAdapter(IAMProperties properties) {
        String provider = properties.getProvider();
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("Property 'igrp.iam.provider' must not be empty");
        }

        try {
            // Build package and class name dynamically
            String capitalized = provider.substring(0, 1).toUpperCase() + provider.substring(1).toLowerCase();
            String className = String.format(
                    "cv.igrp.framework.auth.%s.adapter.%sAdapter",
                    provider.toLowerCase(),
                    capitalized
            );

            // Load class
            Class<?> clazz = Class.forName(className);

            // Ensure it implements IAdapter
            if (!IAdapter.class.isAssignableFrom(clazz)) {
                throw new IllegalArgumentException("Class " + className + " does not implement IAdapter");
            }

            // Instantiate with IAMProperties constructor
            return (IAdapter) clazz
                    .getConstructor(IAMProperties.class)
                    .newInstance(properties);

        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Unsupported IAM provider: " + provider, e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create IAM adapter for provider: " + provider, e);
        }
    }
}
```

#### 4. Conditional Bean Loading

_Provider-Specific Conditional Annotations_
```java
@Configuration
@ConditionalOnIAMProvider("keycloak")
public class KeycloakIAMConfiguration {
    
    @Bean
    public KeycloakClientFactory keycloakClientFactory(IAMProperties properties) {
        // Convert IAMProperties to Keycloak-specific configuration
        return new KeycloakClientFactory(properties);
    }
}

@Configuration
@ConditionalOnIAMProvider("wso2")
public class WSO2IAMConfiguration {
    
    @Bean
    public WSO2ClientFactory wso2ClientFactory(IAMProperties properties) {
        // Convert IAMProperties to WSO2-specific configuration
        return new WSO2ClientFactory(properties);
    }
}
```

#### 5. Custom Conditional Annotation

_Implementation of Conditional Logic_
```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(IAMProviderCondition.class)
public @interface ConditionalOnIAMProvider {
    String value(); // provider name, e.g., "keycloak"
}

public class IAMProviderCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes =
                metadata.getAnnotationAttributes(ConditionalOnIAMProvider.class.getName());

        String requiredProvider = (String) attributes.get("value");
        String configuredProvider = context.getEnvironment()
                .getProperty("igrp.iam.provider", "keycloak")
                .toLowerCase();

        return requiredProvider.equalsIgnoreCase(configuredProvider);
    }
}
```

### Dependency Management Strategy

#### Maven Configuration
```xml
<dependencies>
    <!-- Core IAM abstraction -->
    <dependency>
        <groupId>cv.igrp.framework.auth</groupId>
        <artifactId>core</artifactId>
        <version>${project.version}</version>
    </dependency>
    
    <!-- Provider implementations (all included in a single image) -->
    <dependency>
        <groupId>cv.igrp.framework.auth</groupId>
        <artifactId>keycloak-spring-boot</artifactId>
        <version>${project.version}</version>
    </dependency>
    
    <dependency>
        <groupId>cv.igrp.framework.auth</groupId>
        <artifactId>wso2-spring-boot</artifactId>
        <version>${project.version}</version>
    </dependency>
    
</dependencies>
```

### Spring Autoconfiguration

This approach allows a clean modular split that avoids tight coupling to Spring while still offering Spring Boot autoconfiguration “for free” when needed. This is exactly how larger projects (e.g., AWS SDK, Keycloak SDK, Spring Data drivers) do it.

Here’s how the structure of the three SDKs:

#### 1. `cv.igrp.framework.auth.core`

_Pure Java, no Spring dependencies_

Contains:

* `IAdapter` interface (SPI contract).
* `IAMProperties` (POJO, no Spring annotations).
* `IAMAutoConfigurer` SPI interface.
* Common model classes (User, Role, Permission DTOs).
* Shared utilities (hashing, sync versioning, etc.).

```java
public interface IAdapter {
    // ...
}
```

```java
public interface IAMAutoConfigurer {
    IAdapter createAdapter(IAMProperties properties);
}
```

> This is the **core SPI**. Other frameworks (Spring, Micronaut, Quarkus, etc.) can all build on top of this.

---

#### 2. `cv.igrp.framework.auth.<provider>`

_Java-only provider implementation_

Contains:

* `<Provider>Adapter` class that implements `IAdapter`.
* Provider-specific client configuration.
* Implements `IAMAutoConfigurer`.
* No Spring dependencies.

Example (Keycloak):

```java
public class KeycloakAdapter implements IAdapter {
    private final IAMProperties properties;

    public KeycloakAdapter(IAMProperties properties) {
        this.properties = properties;
    }

    @Override
    public UserIdentity getUser(String username) {
        // Keycloak SDK method call
    }
}
```

```java
public class KeycloakIAMAutoConfigurer implements IAMAutoConfigurer {
    @Override
    public IAdapter createAdapter(IAMProperties properties) {
        return new KeycloakAdapter(properties);
    }
}
```

Register with Java’s **ServiceLoader** mechanism (`META-INF/services`):

`META-INF/services/cv.igrp.framework.auth.core.IAMAutoConfigurer`

```
cv.igrp.framework.auth.keycloak.KeycloakIAMAutoConfigurer
```

Now even without Spring, you can do:

```java
ServiceLoader<IAMAutoConfigurer> loader = ServiceLoader.load(IAMAutoConfigurer.class);
for (IAMAutoConfigurer c : loader) {
    if (c.supports(IAMProvider.KEYCLOAK)) {
        return c.createAdapter(properties);
    }
}
```

---

#### 3. `cv.igrp.framework.auth.<provider>-spring-boot`

_Spring Boot integration layer_

Contains:

* `@ConfigurationProperties` binding for `igrp.iam.*`.
* Autoconfiguration class that discovers the correct adapter using the ServiceLoader or direct instantiation.
* Spring Boot annotations only.

Example (`Keycloak`):

```java
@Configuration
@EnableConfigurationProperties(IAMPropertiesSpring.class)
@ConditionalOnProperty(name = "igrp.iam.provider", havingValue = "keycloak")
public class KeycloakIAMAutoConfiguration {

    @Bean
    public IAdapter keycloakAdapter(IAMPropertiesSpring properties) {
        // Delegate to KeycloakAutoConfigurer via ServiceLoader
        return new KeycloakAdapter(properties.toCoreProperties());
    }
}
```

`IAMPropertiesSpring` is just a wrapper that converts Spring’s `@ConfigurationProperties` into the core `IAMProperties` POJO.

```java
@ConfigurationProperties(prefix = "igrp.iam")
public class IAMPropertiesSpring {
    private String provider;
    private String url;
    private String clientId;
    private String secret;

    public IAMProperties toCoreProperties() {
        return new IAMProperties(provider, url, clientId, secret);
    }
}
```

_Register auto-config_:

For Spring Boot 3+ → `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

```
cv.igrp.framework.auth.keycloak.spring.KeycloakIAMAutoConfiguration
```

---

#### Usage Scenarios

_Java-only (no Spring)_

```java
IAMProperties props = new IAMProperties("keycloak", "http://localhost:8080", "client", "secret");
ServiceLoader<IAMAutoConfigurer> loader = ServiceLoader.load(IAMAutoConfigurer.class);

IAdapter adapter = loader.stream()
    .map(ServiceLoader.Provider::get)
    .findFirst()
    .orElseThrow()
    .createAdapter(props);
```

_Spring Boot_

Add the `-spring-boot` dependency for the provider you need, set properties:

```properties
igrp.iam.provider=keycloak
igrp.iam.url=http://localhost:8080
igrp.iam.client-id=my-client
igrp.iam.secret=my-secret
```

And inject:

```java
@Autowired
private IAdapter iamAdapter;
```

Spring Boot auto-config does the rest.

---

#### Final Module Layout

* **core**

    * `IAdapter`, `IAMProperties`, `IAMProvider`, `IAMAutoConfigurer`
    * DTOs, common utilities

* **keycloak** (Java-only impl)

    * `KeycloakAdapter`
    * `KeycloakIAMAutoConfigurer`
    * META-INF/services entry

* **keycloak-spring-boot** (integration)

    * `KeycloakIAMAutoConfiguration`
    * `IAMPropertiesSpring`
    * `spring.factories` / `AutoConfiguration.imports`

Same for other providers (WSO2, Auth0, etc.).

#### Advantages:

* Core is pure Java (no Spring deps).
* Provider SDKs can be used standalone.
* Spring Boot auto-config works when you add the `-spring-boot` dependency.

### Advantages of This Approach

#### 1. **Single Application Image**
- One Docker image supporting multiple IAM providers
- Provider selection via environment variables
- Reduced DevOps complexity

#### 2. **Standardized Configuration**
- Consistent property structure across providers
- OAuth2 standard compliance
- Easy migration between providers

#### 3. **Extensibility**
- Easy to add new provider implementations
- Plugin architecture for custom providers
- Minimal code changes for new providers

#### 4. **Runtime Flexibility**
- Dynamic provider switching
- Hot configuration reload capability
- Fallback provider support

### Implementation Roadmap

#### Phase 1: Core Abstraction Layer
1. Define `IAMProperties` class
2. Create `IAMAdapterFactory`
3. Implement provider enumeration

#### Phase 2: Adapter Refactoring
1. Refactor existing adapters to use `IAMProperties`
2. Implement provider-specific configuration mapping
3. Create conditional configuration classes

#### Phase 3: Auto-Configuration
1. Implement Spring Boot auto-configuration
2. Create custom conditional annotations
3. Test provider switching functionality

#### Phase 4: Deployment Optimization
1. Create a single Docker image with all providers
2. Update CI/CD pipelines
3. Document configuration examples

### Performance Considerations

#### Classpath Scanning Optimization
- Use `@ConditionalOnClass` to avoid loading unused providers
- Lazy initialization of provider-specific beans
- Minimal reflection usage in the adapter factory

#### Memory Footprint
- Provider SDKs loaded only when needed
- Shared common dependencies
- Efficient bean lifecycle management

### Alternative Approaches Considered

#### 1. **Spring Profiles Approach**
```properties
spring.profiles.active=keycloak
```
- **Pros**: Built-in Spring mechanism
- **Cons**: Limited to one active profile, complex multi-provider scenarios

#### 2. **ServiceLoader Pattern**
- **Pros**: Standard Java SPI, dynamic loading
- **Cons**: More complex configuration, less Spring integration

#### 3. **Database-Driven Configuration**
- **Pros**: Dynamic provider switching, UI configuration
- **Cons**: Added complexity, runtime dependencies

### Recommended Implementation

The **Spring DataSource-like approach** is recommended because:

1. **Familiar pattern** for Spring developers
2. **Strong Spring Boot integration**
3. **Proven scalability** in enterprise environments
4. **Minimal operational overhead**
5. **Excellent tooling support**

This approach allows maintaining a single application image while providing the flexibility to support multiple IAM providers through configuration changes, similar to how Spring DataSource supports multiple databases with a single application deployment.

## 13. Security Considerations

### 13.1 Secrets & Credentials
- Store provider credentials in Vault (or equivalent); do not store in plaintext.
- Rotate service account keys regularly.
- Limit scopes to the least privilege (admin user dedicated to sync).

### 13.2 Rate Limiting & Quotas
- Respect provider rate-limits and provide circuit-breaker patterns.
- Use exponential backoff for retryable errors.

---

## 14. Testing Strategy & Test Cases

### 14.1 Unit Tests
- Adapter tests: mock provider endpoints, validate transformation & idempotency.
- Conflict resolution tests: simulate DB & provider version differences.

### 14.2 Integration Tests
- Spin up Keycloak/WSO2 test container.
- Seed DB test dataset.
- Run command line runner, validate provider objects are in the expected state.
- Test startup reconciliation by modifying provider objects directly and ensuring reconciler repairs.

### 14.3 End-to-End Tests
- Simulate full sign-up flows including social login.
- Validate mapping generation and role assignment.

### 14.4 Performance & Scalability Tests
- Load test: bulk create 100k users and measure outbox processing time.
- Reconciliation test: compute time to reconcile 100k users with two providers.

### 14.5 Security Tests
- Pen tests for outbox endpoint, check injection attacks in payload.
- Validate secret handling and key access.

### 14.6 Example Test Cases (detailed)

#### Test Case: Startup Reconciliation — Missing Role in Provider
- Precondition: DB has three roles; provider has two (ROLE_USER missing).
- Action: Start the application with reconciling enabled.
- Expected: Reconciler creates the missing role in provider.

#### Test Case: Federated User First Login
- Precondition: No mapping exists for `google:sub123`.
- Action: Simulate user invitation indicating federated login with `email_verified=true` and `email=alice@example.com`.
- Expected: If DB has user with same email and `email_verified=true`, mapping created.

#### Test Case: Conflict (Both Changed)
- Precondition: `role` changed in DB, provider changed display_name.
- Action: Reconciler has detected the role is not present anymore.
- Expected: Database applies its changes.

---

## 15. Migration & Rollout Plan

### 15.1 Phase 0 — Outbox + Adapters (Non-intrusive)
- Implement `IAdapter` skeleton and KeycloakAdapter using a test realm.
- Run integration tests.

### 15.2 Phase 1 — Enable Processor (Read-only mode)
- Enable processor in `dry-run` mode where it only logs actions (no writes).
- Run for 1–2 weeks and review logs for unexpected changes.

### 15.3 Phase 2 — Live Sync (Gradual)
- Enable writing to one provider in production (Keycloak).
- Monitor metrics and adjust batch sizes.
- Roll out to other providers gradually.

---

## Closing notes

This document is a comprehensive blueprint for building a production-ready IAM synchronization system. It covers high-level architecture, data models, adapter contracts, sync algorithms, conflict resolution, idempotency, monitoring, testing, and operational procedures.