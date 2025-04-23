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

#### Endpoints

| Método | Endpoint | Request | Response | Status Code |
|--------|----------|---------|----------|-------------|
| POST   | `/api/departments` | `DepartmentDTO` | `DepartmentDTO` | 201 Created |
| GET    | `/api/departments` | — | `List<DepartmentDTO>` | 200 OK |
| GET    | `/api/departments/{id}` | — | `DepartmentDTO` | 200 OK |
| PUT    | `/api/departments/{id}` | `DepartmentDTO` | `DepartmentDTO` | 200 OK |
| DELETE | `/api/departments/{id}` | — | — | 204 No Content |
| GET    | `/api/departments/{id}/roles` | — | `List<RoleDTO>` | 200 OK |
| POST   | `/api/departments/{id}/addRoles` | `List<RoleDTO>` | `List<RoleDTO>` | 200 OK |
| POST   | `/api/departments/{id}/removeRoles` | `List<Integer>` | `List<RoleDTO>` | 200 OK |
| POST   | `/api/departments/{id}/invite` | `IGRPUserDTO` | — | 200 OK |

#### 🔹 Create Departament

- **POST** `/api/departments`  
Creates a new departament.

**📥 Request:**
```json
{
  "code": "TI",
  "name": "Tecnologias de Informação",
  "description": "Departamento responsável pela infraestrutura tecnológica",
  "status": "ACTIVE",
  "application_id": 1,
  "parent_id": null
}
```

**📤 Response:**
```json
{
  "id": 3,
  "code": "TI",
  "name": "Tecnologias de Informação",
  "description": "Departamento responsável pela infraestrutura tecnológica",
  "status": "ACTIVE",
  "application_id": 1,
  "parent_id": null
}
```

#### 🔹 List Departaments

- **GET** `/api/departments`  
List all departaments.

**📤 Response:**
```json
[
  {
    "id": 1,
    "code": "ADM",
    "name": "Administração",
    "description": "Administração Geral",
    "status": "ACTIVE",
    "application_id": 1,
    "parent_id": null
  }
]
```

#### 🔹 Search Departament by ID

- **GET** `/api/departments/{id}`  
Returns the details of a departament.

**📤 Response:**
```json
{
  "id": 1,
  "code": "ADM",
  "name": "Administração",
  "description": "Administração Geral",
  "status": "ACTIVE",
  "application_id": 1,
  "parent_id": null
}
```

#### 🔹 Update Departament

- **PUT** `/api/departments/{id}`  
Updates a departament.

**📥 Request:**
```json
{
  "code": "FIN",
  "name": "Financeiro",
  "description": "Gestão de recursos financeiros",
  "status": "INACTIVE",
  "application_id": 1,
  "parent_id": null
}
```

**📤 Response:** *(Same format as GET by ID)*

#### 🔹 Remove Departament

- **DELETE** `/api/departments/{id}`  
Removes a departament.

**📤 Response:** `204 No Content`

#### 🔸 Department Roles

##### 🔹 List Roles

- **GET** `/api/departments/{id}/roles`  
List all roles related to a departament.

**📤 Response:**
```json
[
  {
    "id": 1,
    "name": "Admin",
    "description": "Acesso completo",
    "departmentId": 1,
    "parentId": null,
    "status": "ACTIVE"
  }
]
```

##### 🔹 Add Roles

- **POST** `/api/departments/{id}/addRoles`  
Adds a list of roles to a departament.

**📥 Request:**
```json
[
  {
    "id": 2,
    "name": "Editor",
    "description": "Permite edição de dados",
    "departmentId": 1
  }
]
```

**📤 Response:** List of `RoleDTO` updated

##### 🔹 Remove Roles

- **POST** `/api/departments/{id}/removeRoles`  
Removes roles from departament.

**📥 Request:**
```json
[2, 3]
```

**📤 Response:** List of remaining `RoleDTO`

#### 🔸 Invite User to a Departament

- **POST** `/api/departments/{id}/invite`  
Associates a new user to the department.

**📥 Request (`IGRPUserDTO`):**
```json
{
  "username": "mrodrigues",
  "name": "Maria Rodrigues",
  "email": "maria@dominio.gov.cv",
  "departmentId": 2,
  "applicationId": 1
}
```

**📤 Response:** `200 OK`

---

### 🔐 Role Management

| Method | Endpoint                          | Request             | Response                  | Status Code                   |
|--------|-----------------------------------|---------------------|---------------------------|-------------------------------|
| POST   | /api/roles                        | RoleDTO             | RoleDTO                   | 201 Created, 404 Not Found    |
| GET    | /api/roles                        | —                   | List&lt;RoleDTO&gt;       | 200 OK                        |
| GET    | /api/roles/{id}                   | —                   | RoleDTO                   | 200 OK, 404 Not Found         |
| PUT    | /api/roles/{id}                   | RoleDTO             | RoleDTO                   | 200 OK, 404 Not Found         |
| DELETE | /api/roles/{id}                   | —                   | —                         | 204 No Content, 404 Not Found |
| GET    | /api/roles/{id}/permissions       | —                   | List&lt;PermissionDTO&gt; | 200 OK, 404 Not Found         |
| POST   | /api/roles/{id}/addPermissions    | List&lt;Integer&gt; | List&lt;PermissionDTO&gt; | 200 OK, 404 Not Found         |
| POST   | /api/roles/{id}/removePermissions | List&lt;Integer&gt; | List&lt;PermissionDTO&gt; | 200 OK, 404 Not Found         |


### 🛂 Permission Management

| Method | Endpoint                            | Request       | Response                  | Status Code                   |
|--------|-------------------------------------|---------------|---------------------------|-------------------------------|
| POST   | /api/permissions                    | PermissionDTO | PermissionDTO             | 201 Created, 404 Not Found    |
| GET    | /api/permissions?applicationId={id} | —             | List&lt;PermissionDTO&gt; | 200 OK                        |
| GET    | /api/permissions/{id}               | —             | PermissionDTO             | 200 OK, 404 Not Found         |
| PUT    | /api/permissions/{id}               | PermissionDTO | PermissionDTO             | 200 OK, 404 Not Found         |
| DELETE | /api/permissions/{id}               | —             | —                         | 204 No Content, 404 Not Found |
| GET    | /api/permissions/{id}/roles         | —             | List&lt;RoleDTO&gt;       | 200 OK, 404 Not Found         |

### 👤 User Management

#### Endpoints

| Método | Endpoint | Request | Response | Status Code |
|--------|----------|---------|----------|-------------|
| POST   | `/api/users/{id}/addRoles` | `List<Integer>` | `List<RoleDTO>` | 201 Created |
| POST   | `/api/users/{id}/removeRoles` | `List<Integer>` | `List<RoleDTO>` | 200 OK |
| GET    | `/api/users` | `?applicationId={id}&departmentId={id}&name={name}&username={username}&email={email}` | `List<UserDTO>` | 200 OK |
| GET    | `/api/users/{id}/roles` | `?applicationId={id}` | `List<RoleDTO>` | 200 OK |

#### 🔹 List Users with Filters

- **GET** `/api/users`  
Search Users applying mandatory filters.

**🔍 Required Parameters:**
- `applicationId`
- `departmentId`
- `name`
- `username`
- `email`

**📤 Response:**
```json
[
  {
    "id": 5,
    "username": "jfernandes",
    "name": "João Fernandes",
    "email": "joao@example.com"
  }
]
```

#### 🔹 Add Roles to an User

- **POST** `/api/users/{id}/addRoles`  
Associates new roles to a User.

**📥 Request:**
```json
[1, 3]
```

**📤 Response:**
```json
[
  {
    "id": 1,
    "name": "Admin",
    "description": "Acesso completo",
    "departmentId": 2,
    "parentId": null,
    "status": "ACTIVE"
  }
]
```

#### 🔹 Remove Roles from an User

- **POST** `/api/users/{id}/removeRoles`  
Remove roles from User.

**📥 Request:**
```json
[3]
```

**📤 Response:** List of remaining `RoleDTO`

#### 🔹 List Roles of User

- **GET** `/api/users/{id}/roles?applicationId={id}`  
Returns the roles associated with the User in the application context.

**📤 Response:** *(Same structure as `RoleDTO`)*
---

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
# Run the Spring Boot app
mvn spring-boot:run
```

---

## 🤝 Contributing

Contributions are welcome! Please open issues or submit pull requests with improvements or new features.

---

## 📄 License

This project is licensed under the Apache 2.0 License. See the [LICENSE](LICENSE) file for details.
