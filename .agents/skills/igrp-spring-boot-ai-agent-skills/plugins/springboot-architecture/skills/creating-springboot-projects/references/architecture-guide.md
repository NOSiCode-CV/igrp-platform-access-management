# iGRP Spring Boot Architecture Guide

## Architecture Decision Matrix

| Criteria | Technical | Domain-lite | Domain |
|----------|-----------|-------------|--------|
| Team Size | 1-5 | 3-10 | 8+ |
| Lifespan | Months to 2 yrs | 2-5 yrs | 5+ yrs |
| CQRS / Events | Optional | Recommended | Mandatory |
| Learning Curve | ⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ |

## Package Structures

### Technical Style
```
com.example.app/
├── controllers/
├── services/
├── repositories/
├── models/
├── dto/
└── constants/
```

**When to use:** Simple CRUD applications, prototypes, MVPs, single module APIs.

### Domain-lite Style
```
com.example.app/
├── products/
│   ├── application/
│   │   ├── commands/
│   │   ├── queries/
│   │   ├── dto/
│   │   └── response/
│   ├── domain/
│   │   ├── events/
│   │   └── repository/
│   └── infrastructure/
│       ├── controller/
│       └── persistence/
├── orders/
│   ├── application/
│   ├── domain/
│   └── infrastructure/
└── shared/
```

**When to use:** Feature modules need CQRS and explicit boundaries without full DDD overhead.

### Domain Style
```
com.example.app/
├── products/
│   ├── application/
│   │   ├── command/
│   │   ├── query/
│   │   └── dto/
│   ├── domain/
│   │   ├── aggregate/
│   │   ├── event/
│   │   ├── service/
│   │   └── repository/
│   └── infrastructure/
│       ├── db/
│       ├── cache/
│       └── spring/
└── shared/
```

**When to use:** Complex business domains, strict CQRS and event-driven flows, long-lived systems.

## Naming Conventions

### Domain-lite / Domain Naming

| Type | Pattern | Example | Package |
|------|---------|---------|---------|
| Command | `*Command` or `*Cmd` | `CreateProductCommand` | `application/command(s)/` |
| Query | `*Query` | `ListProductsQuery` | `application/query(ies)/` |
| Handler | `*Handler` | `CreateProductHandler` | `application/**/handlers/` |
| DTO | `*DTO` | `ProductDTO` | `application/dto/` |
| Response | `*Response` | `ProductResponse` | `application/response/` |
| Event | `*Event` | `ProductCreatedEvent` | `domain/event(s)/` |
| Repository Interface | `*Repository` | `ProductRepository` | `domain/repository/` |
| Repository Impl | `*RepositoryImpl` | `ProductRepositoryImpl` | `infrastructure/persistence/` |
| Controller | `*Controller` | `ProductController` | `infrastructure/controller/` |

### General Conventions

- Use singular names for packages: `product/` not `products/`
- Value Objects are immutable and use `of()` factory method
- Commands are immutable records
- View Models are immutable records for API responses
- Aggregates encapsulate business logic and invariants

## Anti-Patterns to Avoid

| Don't | Do | Why |
|-------|-----|-----|
| Jump to implementation | Ask assessment questions first | Prevents over-engineering or under-engineering |
| Use domain style for simple CRUD | Use technical style | Avoid unnecessary complexity for simple modules |
| Keep module under `domain + rest` only | Use `application + domain + infrastructure` | iGRP module boundaries require explicit layering |
| Skip infrastructure | Always include Flyway, Testcontainers, Docker | Production readiness requires proper infrastructure |
| Copy templates blindly | Read template comments, adapt to domain | Templates are starting points, not solutions |
| Primitive obsession | Use Value Objects for domain concepts | Type safety prevents bugs, makes intent explicit |
| Anemic domain model | Put business logic in entities/aggregates | Domain-Driven Design principle |
| God services | Split by command/query responsibility | CQRS improves maintainability |
| Shared mutable state | Use immutable Value Objects | Thread-safety and predictability |

## Architecture Upgrade Path

### When to Upgrade

| From | To | Trigger | Effort |
|------|-----|---------|--------|
| Technical | Domain-lite | Need command/query separation and module boundaries | Medium |
| Domain-lite | Domain | Need richer domain contracts and infrastructure isolation | High |

### Migration Strategies

**Technical → Domain-lite:**
1. Split each module into `application`, `domain`, and `infrastructure`
2. Move request/response contracts to `application/dto` and `application/response`
3. Introduce command/query handlers and explicit module repository contracts
4. Move controllers to `infrastructure/controller`
5. Move persistence implementations to `infrastructure/persistence`

**Domain-lite → Domain:**
1. Promote domain contracts to aggregate/service/event structure
2. Refine application layer into command/query buses and handlers
3. Split infrastructure into `db`, `cache`, and `spring` integrations
4. Add explicit repository abstraction and implementation boundaries

### Signs You've Outgrown Your Architecture

**Technical:**
- Features span multiple unrelated domains
- Service classes exceed 500 lines
- Difficult to understand feature scope
- Changes affect multiple unrelated features

**Domain-lite:**
- Modules have circular dependencies
- Difficult to test modules in isolation
- Unclear module boundaries
- Need to extract microservices

**Domain-lite with strict module boundaries:**
- Type confusion bugs (mixing IDs, codes, values)
- Validation logic scattered everywhere
- Primitive parameters causing bugs
- Financial/healthcare domain (needs type safety)

**Domain:**
- Complex business rules in multiple aggregates
- Need to swap infrastructure (database, message queue)
- Multiple teams working on same codebase
- Planning microservices extraction

## Best Practices

### Start Simple
- Begin with Technical or Domain-lite
- Don't prematurely optimize for scale
- Upgrade architecture when complexity demands it
- Measure complexity by team size, lifespan, and domain complexity

### Type Safety
- Use Value Objects for domain concepts (not just primitives)
- Leverage Java records for immutability
- Enable JSpecify null-safety (@NullMarked)
- Use sealed classes for states/enums when appropriate

### Module Boundaries
- Each module should have clear responsibility
- Minimize dependencies between modules
- Use events for cross-module communication
- Test modules independently

### Infrastructure
- Always use migrations (Flyway/Liquibase)
- Always use Testcontainers for integration tests
- Always use Docker Compose for local development
- Always use ProblemDetail for REST error responses
