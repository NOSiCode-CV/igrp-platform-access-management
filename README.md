# iGRP Access Management API

The **iGRP Access Management API** is a modular and extensible Identity and Access Management (IAM) solution designed for the [iGRP Business Logic](https://igrp.cv/). It enables the management of users, roles, permissions, applications, and organizational structures, while also supporting integration with external IAM providers like **Keycloak** and **WSO2 Identity Server**, through an abstract adapter layer based on dependency injection.

---

## 🧭 Overview

This module provides identity, authentication, and authorization services to applications built within the iGRP ecosystem. It offers native capabilities as well as integration with external IAM providers. It can operate in standalone mode or delegate authentication/authorization to external systems, ensuring:

- Interoperability with iGRP modules
- Scalability across large infrastructures
- Compliance with modern security standards

---

## 📚 Table of Contents

- [Features](#features)
- [Architecture](#architecture)
- [IAdapter](#iadapter)
- [Database Model](#database-model)
- [API Endpoints](#api-endpoints)
  - [Application Management](#application-management)
  - [Department / Organization Management](#department--organization-management)
  - [Role Management](#role-management)
  - [Permission Management](#permission-management)
  - [User Management](#user-management)
  - [Menu Management](#menu-management)
  - [Resource Management](#resource-management)
- [IAM Provider Integration](#iam-provider-integration)
- [Configuration](#configuration)
- [Running the Project](#running-the-project)
- [Contributing](#contributing)
- [License](#license)

---

## ✨ Features

- Unified API for managing identity and access control
- Plug-and-play integration with IAM providers (Keycloak, WSO2 IS)
- Abstract adapter layer with dependency injection for extensibility
- Fine-grained role and permission management
- Departmental and application-level access control
- RESTful endpoints with secure authentication

---

## 🏗️ Architecture

The system is designed around a modular architecture:

- **Core IAM Module**: Provides core services for user, role, permission, and application management.
- **IAdapter Interface**: Abstracts the communication with external IAM providers.
- **IAM Adapters**: Implementations of `IAdapter` for providers like Keycloak and WSO2.
- **Dependency Injection**: Ensures loose coupling between core logic and external IAM systems.
- **Spring Boot**: The API is built using Spring Boot with layered service and repository components.

---

## 🔌 IAdapter

The `IAdapter` is an abstraction layer that defines the contract for IAM provider integrations. Each provider must implement this interface to ensure consistent behavior across the system.

> **TODO**: Document available methods and usage patterns for the `IAdapter` interface.

---

## 🧩 Database Model

> **TODO**: Add diagrams and table descriptions related to:

- Applications
- Departments/Orgânicas
- Users
- Roles
- Permissions
- Resources
- Menus
- Relations among entities (e.g., user-role, role-permission)

---

## 📡 API Endpoints

### 📦 Application Management

> **TODO**: Document endpoints related to managing applications within the IAM module.

### 🏢 Department / Organization Management

> **TODO**: Document endpoints related to managing organizational units and departments.

### 🔐 Role Management

> **TODO**: Document endpoints related to creating, updating, and assigning roles.

### 🛂 Permission Management

> **TODO**: Document endpoints to define and assign granular permissions.

### 👤 User Management

> **TODO**: Document endpoints for user lifecycle (create, update, delete) and role/permission assignments.

### 🧭 Menu Management

> **TODO**: Document endpoints for managing UI menus and access control.

### 🧱 Resource Management

> **TODO**: Document endpoints for handling protected resources and their associated policies.

---

## 🌍 IAM Provider Integration

This project supports integration with third-party IAM systems using the `IAdapter` strategy:

- **KeycloakAdapter**: Handles interactions with Keycloak
- **WSO2Adapter**: Handles interactions with WSO2 Identity Server

To switch providers, update the `application.properties` file:

```properties
app.auth.provider=keycloak
# or
app.auth.provider=wso2
```

The appropriate adapter will be injected automatically based on this configuration.

---

## ⚙️ Configuration

Profile-specific configuration can be managed via:

- `application-dev.properties`
- `application-prod.properties`

Supports:
- Database settings
- IAM provider configuration
- JWT settings
- Mail server config (optional)

---

## 🚀 Running the Project

```bash
# Build the project
mvn clean install

# Run the Spring Boot app
java -jar target/igrp-access-api.jar
```

---

## 🤝 Contributing

Contributions are welcome! Please open issues or submit pull requests with improvements or new features.

---

## 📄 License

This project is licensed under the Apache 2.0 License. See the [LICENSE](LICENSE) file for details.