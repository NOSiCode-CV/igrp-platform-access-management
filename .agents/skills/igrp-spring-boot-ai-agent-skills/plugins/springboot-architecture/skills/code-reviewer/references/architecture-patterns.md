# Architecture Patterns

## Table of Contents

1. [Architecture Patterns Overview](#architecture-patterns-overview)
2. [Detailed Comparison Table](#detailed-comparison-table)
3. [Progressive Evolution Path](#progressive-evolution-path)
4. [Technical Style](#layered-architecture)
5. [Domain Style](#package-by-module-architecture)
6. [Domain with Modulith Boundaries](#simple-modular-monolith)
7. [Domain-Driven Design (DDD)](#domain-driven-design)
8. [Hexagonal Architecture](#hexagonal-architecture)
9. [Spring Modulith](#spring-modulith)
10. [CQRS](#cqrs)
11. [Event-Driven Architecture](#event-driven-architecture)
12. [Decision Guide](#decision-guide)

---

## Architecture Patterns Overview

This guide demonstrates iGRP-aligned architectural approaches, progressively increasing in sophistication:

1. **Technical Style** - Traditional package-by-layer approach (controllers, services, repositories)
2. **Domain Style** - Module-based approach with `application/domain/infrastructure`
3. **Domain + Modulith** - Module boundaries enforced with Spring Modulith
5. **Domain + Hexagonal** - Full domain-driven design with clear application/domain/infrastructure boundaries

**Guiding Principle:** Start with the simplest architecture that meets your needs, and evolve as complexity demands it. It's easier to fix an under-engineered system than an over-engineered one.

**Related Guides:**
- See [domain-modeling.md](domain-modeling.md) for Anemic vs Rich domain models
- See [value-objects-patterns.md](value-objects-patterns.md) for Value Object patterns

---

## Detailed Comparison Table

| Aspect | Technical | Domain | Domain + Modulith | Domain + Hexagonal |
|--------|---------|-------------------|----------------|-----------------|
| **Code Organization** | By layer (controller, service, repository, domain) | By feature/module (orders, catalog, users) | By module | By module with layers inside |
| **Domain Model Type** | **Anemic** (getters/setters only) | **Anemic** | **Anemic** | **Rich** (with behavior) |
| **Data Types** | Primitives (`String`, `Integer`, `BigDecimal`) | Primitives | Primitives | **Value Objects** |
| **Domain/Persistence Separation** | ❌ JPA entities = Domain models | ❌ JPA entities = Domain models | ❌ JPA entities = Domain models | ✅ Separate domain & persistence models |
| **Business Logic Pattern** | Transaction Script (in Services) | Transaction Script | Transaction Script | Rich domain models + Use Cases |
| **Cross-Module Communication** | Spring Events (`@EventListener`) | Spring Events | **Spring Modulith Persistent Events** | Spring Events |
| **Module Boundary Enforcement** | ❌ None | ❌ None | ✅ `modules.verify()` | ✅ ArchUnit for Hexagonal |
| **CQRS Support** | ❌ No | ✅ Separate Command & Query | ✅ Separate Command & Query | ✅ Separate Command & Query | ✅ Separate Command & Query |
| **Validation** | In services (defensive coding everywhere) | In services | In services | In Value Object constructors |
| **Testing Strategy** | Integration tests | Integration tests | Integration tests + **Modularity tests** | Integration tests + **ArchUnit tests** |
| **Complexity** | ⭐ Low | ⭐⭐ Low-Medium | ⭐⭐ Medium | ⭐⭐⭐⭐ High |
| **Learning Curve** | Easy | Easy | Moderate | Steep |
| **Maintenance Effort** | Low (for simple apps) | Medium | Medium | High |
| **Best For** | Simple microservices, CRUD apps | Simple-to-medium apps with features | Apps needing module boundaries | Complex apps with subdomains |
| **Team Size** | 1-3 | 3-10 | 5-15 | 10+ |
| **Expected Lifespan** | Months | 1-2 years | 2-5 years | 5+ years |

---

## Progressive Evolution Path

The most successful approach is to start simple and evolve:

```
Technical Style
    ↓ (reorganize by feature - Easy, add CQRS - Hard)
Domain Style
    ↓ (add Spring Modulith - Moderate)
Domain + Modulith
    ↓ (add Value Objects, rich entities, separate domain/infra - Moderate-Hard)
Domain + Hexagonal
```

### Level 1: Technical → Domain

**Key Improvements:**
- ✅ Better modularity by feature
- ✅ Added CQRS support
- ✅ Easier to find related code
- ✅ Better team alignment

**Migration:** Reorganize from `models/order/Order.java` to `orders/infrastructure/persistence/entity/OrderEntity.java`;
Services from `createOrder()` in `services/OrderService.java` to `orders/application/commands/CreateOrder.java` 
(input data model class) and `orders/application/commands/CreateOrderCommandHandler.java` (business logic)

### Level 2: Domain → Domain + Modulith

**Key Improvements:**
- ✅ Automated module boundary verification
- ✅ Persistent events for reliable communication
- ✅ Event replay capabilities

**New Additions:** `@ApplicationModuleListener`, `ModularityTest.java`

### Level 3: Domain + Modulith → Domain + Hexagonal

**Key Improvements:**
- ✅ Type safety with Value Objects
- ✅ Fail-fast validation
- ✅ Rich domain behavior
- ✅ Less defensive coding
- ✅ Complete domain/infrastructure separation
- ✅ CQRS-ready architecture
- ✅ Ports & Adapters pattern
- ✅ ArchUnit tests for compliance

**See:** [value-objects-patterns.md](value-objects-patterns.md) for VO examples

---

## Technical Style

The simplest pattern and a good starting point for small CRUD-style services.

### Structure

```
com.example.app
├── controllers/           # REST endpoints and service interface (I[controllerName]Service.java)
├── services/              # Business logic
├── dto/                   # DTOs / view models
└── models/
    └── [entityName]/         # JPA entities (typically anemic)
        ├── Entity.java           # Data models
        └── EntityRepository.java # Data access
```

### When to Use
- Small teams (1–3 devs), short-lived apps, or simple microservices
- Domain complexity is low; rich domain model not needed yet
- Rapid prototyping or proof-of-concept
- Simple CRUD operations with minimal business logic

### Avoid When
- Application will grow complex over time
- Multiple teams working on different features
- Domain logic is significant and evolving

### Example

✅ **Proper layering**
```java
@RestController
@RequestMapping("/users")
public class UserController {
    private final IUserService userService;  // Controller → Service

    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUser(@PathVariable Long id) {
        return userService.findById(id);
    }
}

public interface IUserService {
    ResponseEntity<UserDTO> findById(Long id);
}

@Service
public class UserService implements IDepartmentService {
    private final UserRepository userRepository;  // Service → Repository

    public ResponseEntity<UserDTO> findById(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException(id));
        return ResponseEntity.ok(UserMapper.toDTO(user));
    }
}
```

❌ **Layer violations**
```java
@RestController
public class OrderController {
    private final OrderRepository orderRepository;  // Controller → Repository (BAD!)

    @GetMapping("/orders/{id}")
    public Order getOrder(@PathVariable Long id) {
        return orderRepository.findById(id).orElseThrow();  // No business logic!
    }
}
```

**Review Checklist:**
- [ ] Controllers → Services only; no direct repository access
- [ ] Services own business rules; controllers only orchestrate I/O
- [ ] Entities never returned in API; DTOs/view models used
- [ ] Validate invariants somewhere (service or emerging value objects)

---

## Domain Style

Feature-first structure grouped per business module, with explicit `application`, `domain`, and `infrastructure` boundaries.

### Structure
```
com.example.app
├── orders/                    # Order module
│   ├── application/
│   │   ├── commands/
│   │   ├── dto/
│   │   └── queries/
│   ├── domain/
│   │   ├── events/
│   │   ├── exceptions/
│   │   ├── models/
│   │   ├── repository/
│   │   └── service/
│   ├── infrastructure/
│   │   ├── messaging/
│   │   ├── persistence/
│   │   └── spring/
│   └── interfaces/
│       └── rest/
├── catalog/                   # Catalog module
├── payments/                  # Payment module
└── shared/                    # Cross-cutting only (config, security, shared elements)
    ├── application/
    ├── config/
    ├── domain/
    ├── infrastructure/
    └── security/
```

### Choose When
- 3–10 person teams with clear feature ownership
- Medium complexity; want easier navigation
- Need clearer bounded contexts than technical style
- May extract to microservices later

### Avoid When
- App is tiny (technical style is simpler)
- Need hard module boundaries (go Modulith)

### Common Pitfalls

**See:** [domain-modeling.md](domain-modeling.md) for detailed explanations of:
- Anemic domain models (entities are just data bags)
- Primitive obsession (using `String`, `int` for domain concepts)
- Invalid states (no invariants enforced)
- Cross-layer leakage (controllers bypassing services)

**Review Checklist:**
- [ ] Code grouped by module and split into `application`, `domain`, `infrastructure`
- [ ] Shared package is minimal and generic; no business logic
- [ ] Cross-module calls go through application services or events
- [ ] Controllers stay under `interfaces/rest` inside each module

---

## Domain with Modulith Boundaries

Domain architecture plus Spring Modulith boundary enforcement and persistent events.

### What's Added
- `ApplicationModules.of(App.class).verify()` tests to prevent forbidden dependencies
- `@ApplicationModuleListener` for transactional, persistent cross-module events (replayable, retried)
- Module metadata for visualization (optional)

### Choose When
- Need reliable module isolation but still a monolith
- Require durable cross-module messaging without a broker
- Want an easy migration path toward microservices later

### Avoid When
- Overhead isn't justified (very small apps)
- Need full domain/persistence separation (go Domain or Domain + Hexagonal)

### Example: Module Events

```java
// Publishing events
@Service
public class OrderService {
    private final ApplicationEventPublisher eventPublisher;

    public Order createOrder(CreateOrderRequest request) {
        Order order = orderRepository.save(new Order(request));
        eventPublisher.publishEvent(new OrderCreatedEvent(order.getId()));
        return order;
    }
}

// Listening to events (in different module)
@Service
public class PaymentService {
    @ApplicationModuleListener  // Spring Modulith annotation
    public void onOrderCreated(OrderCreatedEvent event) {
        processPayment(event.orderId());
    }
}
```

### Module Verification Test

```java
@SpringBootTest
class ModulithTest {
    @Test
    void verifyModules() {
        ApplicationModules.of(Application.class)
            .verify();  // Fails if module boundaries violated
    }
}
```

**Review Checklist:**
- [ ] Modules.verify() exists and passes in tests
- [ ] Cross-module communication uses `@ApplicationModuleListener` events
- [ ] Internal types kept package-private or under `internal/`
- [ ] Event publication is transactional

---

## Full Domain-Driven Design Style

Value-object-heavy, richer domain within a modular monolith. JPA entities embed VOs; behavior moves into aggregates.

### Characteristics
- **Value Objects** for domain concepts; validation in constructors (fail fast)
- **Spring converters** to map request strings → VOs automatically
- **Richer entity behavior** instead of pure transaction scripts
- **Type safety** - impossible to confuse `OrderNumber` with `String`

**See:** [value-objects-patterns.md](value-objects-patterns.md) for detailed VO patterns and examples

### Simple Example

```java
// Value Object (see value-objects-patterns.md for full implementation)
public record OrderNumber(@JsonValue String value) { /* validation in constructor */ }

// Rich Entity
public class Order {
    
    private OrderNumber orderNumber;  // ✅ Value Object, not String
    
    private Money total;  // ✅ Value Object, not BigDecimal
    
    private OrderStatus status;

    // ✅ Factory method
    public static Order createNew(Money total) {
        return new Order(OrderNumber.generate(), total, OrderStatus.PENDING);
    }

    // ✅ Business behavior
    public void cancel() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("Cannot cancel " + status + " orders");
        }
        this.status = OrderStatus.CANCELLED;
    }

    // ✅ No setters, only getters
    public OrderNumber getOrderNumber() { return orderNumber; }
}
```

### Choose When
- Domain is medium complexity; type safety matters
- Team is comfortable with VO/rich model patterns
- Want to reduce defensive coding in services
- Need infrastructure-independence
- Financial, healthcare, or domains where type safety is critical

### Avoid When
- Domain is trivial (stay technical/domain)
- Team unfamiliar with DDD concepts

**Review Checklist:**
- [ ] Core concepts use VOs (OrderNumber, Money, Quantity)
- [ ] Validation enforced at creation; primitives not leaking
- [ ] Spring converters registered for external → VO mapping
- [ ] Business rules live on aggregates; services orchestrate only

---

## Hexagonal Architecture

**Choose When:** Need technology independence, port swapping (databases, gateways), strong testability.
**Avoid When:** Domain is simple and adapter indirection adds needless ceremony.

Also known as "Ports and Adapters".

### Structure

```
com.example.app
├── domain/             # Core business logic (no dependencies)
│   ├── model/
│   │   ├── User.java
│   │   └── Order.java
│   ├── port/           # Interfaces (ports)
│   │   ├── in/         # Use cases (incoming)
│   │   │   └── CreateOrderUseCase.java
│   │   └── out/        # External dependencies (outgoing)
│   │       ├── OrderRepository.java
│   │       └── PaymentGateway.java
│   └── service/        # Domain services
│       └── OrderService.java
├── adapter/
│   ├── in/             # Input adapters
│   │   ├── rest/
│   │   │   └── OrderController.java
│   │   └── messaging/
│   │       └── OrderEventListener.java
│   └── out/            # Output adapters
│       ├── persistence/
│       │   └── OrderJpaAdapter.java
│       └── payment/
│           └── StripePaymentAdapter.java
└── config/             # Wiring
    └── ApplicationConfig.java
```

### Example

✅ **Port (interface in domain)**
```java
// domain/port/in/CreateOrderUseCase.java
public interface CreateOrderUseCase {
    Order createOrder(CreateOrderCommand command);
}

// domain/port/out/OrderRepository.java
public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(Long id);
}
```

✅ **Domain service implements use case**
```java
// domain/service/OrderService.java
@Service
public class OrderService implements CreateOrderUseCase {
    private final OrderRepository orderRepository;       // Out port
    private final PaymentGateway paymentGateway;         // Out port

    @Override
    public Order createOrder(CreateOrderCommand command) {
        Order order = new Order(command);
        PaymentResult result = paymentGateway.processPayment(order.getPayment());
        if (!result.isSuccess()) {
            throw new PaymentFailedException();
        }
        return orderRepository.save(order);
    }
}
```

✅ **Output adapter (persistence)**
```java
// adapter/out/persistence/OrderJpaAdapter.java
@Component
public class OrderJpaAdapter implements OrderRepository {  // Implements out port
    private final OrderJpaRepository jpaRepository;

    @Override
    public Order save(Order order) {
        OrderEntity entity = toEntity(order);
        OrderEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Order> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }
}
```

**Benefits:**
- Domain logic independent of frameworks
- Easy to test (mock ports)
- Easy to swap implementations (e.g., Stripe → PayPal)

**Review Checklist:**
- [ ] Domain has no Spring/JPA annotations
- [ ] All external dependencies behind ports (interfaces)
- [ ] Adapters implement ports
- [ ] Domain logic testable without Spring context

---

## Spring Modulith

**Choose When:** Want module boundary enforcement and durable intra-monolith events.
**Avoid When:** Boundaries aren't important or need full domain/infra separation.

Spring Modulith enforces module boundaries at runtime.

### Module Structure

```
com.example.app
├── order/                  # Module
│   ├── Order.java
│   ├── OrderService.java
│   ├── OrderRepository.java
│   ├── OrderController.java
│   └── internal/           # Internal (not accessible from other modules)
│       └── OrderValidator.java
├── catalog/                # Module
└── payment/                # Module
```

### Module Dependencies

✅ **Public API**
```java
// order/Order.java
package com.example.app.order;

@Entity
public class Order {  // Public - accessible from other modules
    @Id
    private Long id;
}

// order/OrderService.java
@Service
public class OrderService {  // Public
    public Order createOrder(CreateOrderRequest request) { ... }
}
```

✅ **Internal implementation**
```java
// order/internal/OrderValidator.java
package com.example.app.order.internal;

@Component
class OrderValidator {  // Package-private - only accessible within 'order' module
    boolean isValid(Order order) { ... }
}
```

❌ **Violating module boundary**
```java
// payment/PaymentService.java
import com.example.app.order.internal.OrderValidator;  // Compile error!

@Service
public class PaymentService {
    private final OrderValidator validator;  // Cannot access internal!
}
```

**Review Checklist:**
- [ ] Modules organized by business domain
- [ ] Internal classes in `internal` package or package-private
- [ ] Cross-module communication via events
- [ ] Module boundaries verified in tests

---

## CQRS

**Choose When:** Read/write workloads differ, need denormalized read models, eventual consistency acceptable.
**Avoid When:** Simple CRUD or dual models add needless complexity.

Command Query Responsibility Segregation.

### Basic Pattern

✅ **Separate read and write models**
```java
// Write model (commands)
@Service
public class OrderCommandService {
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public Order createOrder(CreateOrderCommand command) {
        Order order = new Order(command);
        Order saved = orderRepository.save(order);
        eventPublisher.publishEvent(new OrderCreatedEvent(saved.getId()));
        return saved;
    }
}

// Read model (queries)
@Service
public class OrderQueryService {
    private final OrderReadRepository orderReadRepository;

    public OrderDTO findById(Long id) {
        return orderReadRepository.findDTOById(id)
            .orElseThrow(() -> new OrderNotFoundException(id));
    }

    public Page<OrderSummaryDTO> findAll(Pageable pageable) {
        return orderReadRepository.findAllSummaries(pageable);
    }
}
```

**Review Checklist:**
- [ ] Commands and queries separated
- [ ] Write model normalized (enforces invariants)
- [ ] Read model denormalized (optimized for queries)
- [ ] Event handlers keep read model in sync

---

## Event-Driven Architecture

**Choose When:** Need loose coupling, asynchronous workflows, multiple consumers of business facts.
**Avoid When:** Work is strictly request/response and consistency must be immediate.

### Domain Events

✅ **Define events**
```java
public record OrderCreatedEvent(
    Long orderId,
    Long customerId,
    BigDecimal total,
    LocalDateTime createdAt
) {}
```

✅ **Publish events**
```java
@Service
public class OrderService {
    private final ApplicationEventPublisher eventPublisher;

    public Order createOrder(CreateOrderRequest request) {
        Order order = orderRepository.save(new Order(request));
        eventPublisher.publishEvent(new OrderCreatedEvent(
            order.getId(),
            order.getCustomerId(),
            order.getTotal(),
            order.getCreatedAt()
        ));
        return order;
    }
}
```

✅ **Listen to events**
```java
@Service
public class InventoryService {
    @EventListener
    public void onOrderCreated(OrderCreatedEvent event) {
        reserveItems(event.orderId());
    }
}

@Service
public class NotificationService {
    @Async
    @EventListener
    public void onOrderCreated(OrderCreatedEvent event) {
        sendOrderConfirmation(event.customerId(), event.orderId());
    }
}
```

### Transactional Events

✅ **Publish after transaction commits**
```java
@Service
public class OrderService {
    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        Order order = orderRepository.save(new Order(request));
        // Event only published if transaction commits
        eventPublisher.publishEvent(new OrderCreatedEvent(order.getId()));
        return order;
    }
}

@Service
public class InventoryService {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        // Only called after order transaction commits
        reserveItems(event.orderId());
    }
}
```

**Review Checklist:**
- [ ] Domain events used for cross-module communication
- [ ] Events published after transaction commits
- [ ] Event handlers idempotent (can be called multiple times safely)
- [ ] Event versioning strategy in place

---

## Decision Guide

### Quick Decision Matrix

| Pattern                   | Domain Complexity | Team Size | Type Safety | Module Boundaries | Best For |
|---------------------------|-------------------|-----------|-------------|-------------------|----------|
| Technical                 | Low | 1–5 | Low | None | Small CRUD services, prototypes |
| Domain                    | Low–Medium | 3–10 | Medium | Soft | Feature-owned teams with explicit module structure |
| Domain + Modulith         | Low–Medium | 5–15 | Medium | Hard | Monoliths needing enforced boundaries & durable events |
| Full Domain-Driven Design | Medium | 5–15 | High | Hard | Type-safe domains with explicit command/query/event design |
| Domain + Hexagonal        | High | 10+ | High | Hard | Complex, long-lived domains, infra swap/CQRS ready |

### When to Choose Each Pattern

#### 🎯 Technical Style

**Choose When:** Simple CRUD, small microservices, prototyping, minimal domain logic, team 1-3 people, lifespan months

**Example Use Cases:** Admin dashboards, simple REST APIs, internal tools, MVPs

---

#### 🎯 Domain Style

**Choose When:** Medium apps, 3-5 bounded contexts, feature ownership, team 3-10 people, may extract to microservices later

**Example Use Cases:** E-commerce (catalog, orders, users), social platforms (posts, users, messaging), CMS

---

#### 🎯 Domain with Modulith Boundaries

**Choose When:** Need guaranteed module boundaries, durable cross-module events, event replay, team 5-15 people

**Example Use Cases:** Multi-tenant SaaS, enterprise apps with subdomains, systems needing audit trails, microservices migration candidates

---

#### 🎯 Full Domain-Driven Design Style

**Choose When:** Moderate domain complexity, type safety important, want rich domain full DDD, team 5-15 people, lifespan 3-5 years

**Example Use Cases:** Financial apps, healthcare systems, booking systems, inventory management, order processing

---

#### 🎯 Domain + Hexagonal

**Choose When:** Complex business domains, long-lived apps (5+ years), domain logic must be independent, CQRS beneficial, team 10+ people

**Example Use Cases:** Banking systems, insurance processing, complex e-commerce, trading platforms, supply chain management

### Signs You Need to Evolve

| Current                       | Sign to Evolve                                            | Next Step                   |
|-------------------------------|-----------------------------------------------------------|-----------------------------|
| **Technical**                 | Hard to isolate feature evolution and contracts           | → Domain                    |
| **Domain**                    | Accidental cross-module dependencies, unreliable events   | → Domain + Modulith         |
| **Domain + Modulith**         | Need richer domain contracts and stronger invariants      | → Full Domain Driven Design |
| **Full Domain-Driven Design** | Need infrastructure ports/adapters and stricter isolation | → Domain + Hexagonal        |

---

## Summary

### Quick Reference

| Pattern                       | One-Liner                                    | When to Use                                   |
|-------------------------------|----------------------------------------------|-----------------------------------------------|
| **Technical**                 | Quick and simple for basic CRUD              | Small apps, prototypes, minimal domain logic  |
| **Domain**                    | Organized by module with explicit layers     | Medium apps with clear feature ownership      |
| **Domain + Modulith**         | Domain-lite with guaranteed boundaries       | Need module isolation and persistent events   |
| **Full Domain-Driven Design** | Rich domain contracts with CQRS/event flow   | Medium-high complexity, need strict contracts |
| **Domain + Hexagonal**        | Full domain independence for complex systems | Complex domains, long-lived, evolving         |

### The Golden Rules

1. **Start Simple** - Choose the simplest architecture that solves your problem
2. **Evolve Gradually** - It's easier to add complexity than remove it
3. **Listen to Pain Points** - Let actual problems guide architectural evolution
4. **Consider Team Experience** - Don't jump to DDD if team isn't ready
5. **Value Delivery Over Purity** - Ship working software, refine architecture later

---

## Official Documentation

- [Structuring Your Code - Spring Boot](https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html)
- [Spring Modulith Reference](https://docs.spring.io/spring-modulith/reference/)
- [Domain-Driven Design (DDD) - Martin Fowler](https://martinfowler.com/tags/domain%20driven%20design.html)
- [Hexagonal Architecture - Alistair Cockburn](https://alistair.cockburn.us/hexagonal-architecture/)
- [Spring Data JPA Reference](https://docs.spring.io/spring-data/jpa/reference/)
- [Spring Events Documentation](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events)
- [CQRS Pattern - Martin Fowler](https://martinfowler.com/bliki/CQRS.html)
- [Anemic Domain Model - Martin Fowler](https://martinfowler.com/bliki/AnemicDomainModel.html)

---

**Remember:** The best architecture is the one that serves your current needs while allowing for future growth. Don't over-engineer, but don't paint yourself into a corner either. Start simple, ship value, and evolve thoughtfully.
