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
- [Code Quality & Reports](#code-quality--reports)
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

#### Endpoints

| Método | Endpoint                                      | Request          | Response | Status Code    |
|--------|-----------------------------------------------|------------------|--|----------------|
| POST   | `/api/applications`                           | `ApplicationDTO` | `ApplicationDTO` | 201 Created    |
| GET    | `/api/applications`                           | —                | `List<ApplicationDTO>` | 200 OK         |
| GET    | `/api/applications/{id}`                      | —                | `ApplicationDTO` | 200 OK         |
| PUT    | `/api/applications/{id}`                      | `ApplicationDTO` | `ApplicationDTO` | 200 OK         |
| DELETE | `/api/applications/{id}`                      | —                | — | 204 No Content |
| GET    | `/api/applications/denied-to-user/{uid}`      | —                | `List<ApplicationDTO>` | 200 OK         |
| GET    | `/api/applications/by-user/{uid}`             | —                | `List<ApplicationDTO>` | 200 OK         |
| POST   | `/api/applications/{id}/custom-fields`        | `Map<String, ?>` | — | 204 No Content |
| POST   | `/api/applications/{id}/custom-fields/remove` | `List<String>`   | — | 204 No Content |
| GET    | `/api/applications/{id}/custom-fields`        | —                |  `Map<String, ?>` | 200 OK         |
| GET    | `/api/applications/by-ids`                    | `List<Integer>`  |   `List<ApplicationDTO>`| 200 OK         |

#### 🔹 Create Application

- **POST** `/api/applications`  
  Creates a new application.

**📥 Request:**
```json
{
  "code": "string",
  "name": "string",
  "description": "string",
  "status": "ACTIVE",
  "type": "EXTERNAL",
  "owner": "string",
  "picture": "string",
  "url": "https://example.com/",
  "slug": "string"
}
```

**📤 Response:**
```json
{
  "id": 1073741824,
  "code": "string",
  "name": "string",
  "description": "string",
  "status": "ACTIVE",
  "type": "EXTERNAL",
  "owner": "string",
  "picture": "string",
  "url": "https://example.com/",
  "slug": "string",
  "createdBy": "string",
  "createdDate": "string",
  "lastModifiedBy": "string",
  "lastModifiedDate": "string"
}
```

#### 🔹 List Applications

- **GET** `/api/applications`
- **Query Params**:
  - `code` (optional): string
  - `name` (optional): string
  
**📤 Response:**
```json
[
  {
    "id": 1073741824,
    "code": "string",
    "name": "string",
    "description": "string",
    "status": "ACTIVE",
    "type": "EXTERNAL",
    "owner": "string",
    "picture": "string",
    "url": "https://example.com/",
    "slug": "string",
    "createdBy": "string",
    "createdDate": "string",
    "lastModifiedBy": "string",
    "lastModifiedDate": "string"
  }
]
```

#### 🔹 Search Application by ID

- **GET** `/api/applications/{id}`

**📤 Response:**
```json
{
  "id": 1073741824,
  "code": "string",
  "name": "string",
  "description": "string",
  "status": "ACTIVE",
  "type": "EXTERNAL",
  "owner": "string",
  "picture": "string",
  "url": "https://example.com/",
  "slug": "string",
  "createdBy": "string",
  "createdDate": "string",
  "lastModifiedBy": "string",
  "lastModifiedDate": "string"
}
```

#### 🔹 Update Application

- **PUT** `/api/applications/{id}`

**📥 Request:**
```json
{
  "code": "string",
  "name": "string",
  "description": "string",
  "status": "ACTIVE",
  "type": "EXTERNAL",
  "owner": "string",
  "picture": "string",
  "url": "https://example.com/",
  "slug": "string"
}
```

**📤 Response:** *(Same format as GET by ID)*

#### 🔹 Remove Application

- **DELETE** `/api/applications/{id}`

**📤 Response:** `204 No Content`

#### 🔹 Application Custom Fields

- **GET** `/api/applications/{id}/custom-fields`

**📤 Response:**
```json
{
  "field1": "value1",
  "field2": 69,
  "field3": {
    "field4": "value4"
  }
}
```

#### 🔹 Application Add Custom Fields

- **POST** `/api/applications/{id}/custom-fields`

**📥 Request:**
```json
{
  "field1": "value1",
  "field2": "value2"
}
```
**📤 Response:** `204 No Content`

#### 🔹 Application Remove Custom Fields

- **POST** `/api/applications/{id}/custom-fields/remove`

**📥 Request:**
```json
["field1", "field2"]
```
**📤 Response:** `204 No Content`

#### 🔹 List Applications By Ids

- **POST** `/api/applications/by-ids`

**📥 Request:**
```json
[69, 99]
```
**📤 Response:**
```json
[
  {
    "id": 69,
    "code": "string",
    "name": "string",
    "description": "string",
    "status": "ACTIVE"
  },
  {
    "id": 99,
    "code": "string",
    "name": "string",
    "description": "string",
    "status": "ACTIVE"
  }
]
```

#### 🔹 Allowed Application by UID (User Identifier)

- **GET** `/api/applications/by-user/{uid}`

**📤 Response:**
```json
[{
  "id": 1073741824,
  "code": "string",
  "name": "string",
  "description": "string",
  "status": "ACTIVE",
  "type": "EXTERNAL",
  "owner": "string",
  "picture": "string",
  "url": "https://example.com/",
  "slug": "string",
  "createdBy": "string",
  "createdDate": "string",
  "lastModifiedBy": "string",
  "lastModifiedDate": "string"
}]
```

#### 🔹 Denied Application by UID (User Identifier)

- **GET** `/api/applications/denied-to-user/{uid}`

**📤 Response:**
```json
[{
  "id": 1073741824,
  "code": "string",
  "name": "string",
  "description": "string",
  "status": "ACTIVE",
  "type": "EXTERNAL",
  "owner": "string",
  "picture": "string",
  "url": "https://example.com/",
  "slug": "string",
  "createdBy": "string",
  "createdDate": "string",
  "lastModifiedBy": "string",
  "lastModifiedDate": "string"
}]
```

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

| Method | Endpoint                          | Request             | Response                  | Status Code                                 |
|--------|-----------------------------------|---------------------|---------------------------|---------------------------------------------|
| POST   | /api/roles                        | RoleDTO             | RoleDTO                   | 201 Created, 404 Not Found, 400 Bad Request |
| GET    | /api/roles                        | —                   | List&lt;RoleDTO&gt;       | 200 OK                                      |
| GET    | /api/roles/{id}                   | —                   | RoleDTO                   | 200 OK, 404 Not Found                       |
| PUT    | /api/roles/{id}                   | RoleDTO             | RoleDTO                   | 200 OK, 404 Not Found                       |
| DELETE | /api/roles/{id}                   | —                   | —                         | 204 No Content, 404 Not Found               |
| GET    | /api/roles/{id}/permissions       | —                   | List&lt;PermissionDTO&gt; | 200 OK, 404 Not Found                       |
| POST   | /api/roles/{id}/addPermissions    | List&lt;Integer&gt; | List&lt;PermissionDTO&gt; | 200 OK, 404 Not Found                       |
| POST   | /api/roles/{id}/removePermissions | List&lt;Integer&gt; | List&lt;PermissionDTO&gt; | 200 OK, 404 Not Found                       |

#### Rules
- Permission `name` must be unique within the same Department.
- Names are **case-insensitive** (e.g., `role_delete_user` and `ROLE_DELETE_USER` are considered duplicates).


#### Schemas
##### RoleDTO
```json
{
  "id": 1073741824,
  "name": "string",
  "description": "string",
  "departmentId": 1073741824,
  "parentId": 1073741824,
  "status": "ACTIVE"
}
```
#### Create Role
- **POST** `/api/roles/:id`
##### Request & Response
```json
{
  "id": 1073741824,
  "name": "string",
  "description": "string",
  "departmentId": 1073741824,
  "parentId": 1073741824,
  "status": "ACTIVE"
}
```

#### Get Roles
##### Response
```json
[
    {
        "id": 355,
        "name": "Parent",
        "description": "Create Role Create Role",
        "departmentId": 1,
        "parentId": null,
        "status": "ACTIVE"
    },
    {
        "id": 356,
        "name": "Create Child2",
        "description": "Create Role Create Role",
        "departmentId": 1,
        "parentId": 355,
        "status": "ACTIVE"
    }
]
```

#### Get Roles by ID
##### Request
- **GET** `/api/roles/:id`

##### 200 OK Response
```json
 {
  "id": 204,
  "name": "Role1: New Name",
  "description": "Role1: New Name Description",
  "departmentId": 1,
  "parentId": null,
  "status": "ACTIVE"
}
```
##### 404 Not Found Response
```json
{
  "status": "NOT_FOUND",
  "title": "Get Role By Id",
  "details": "Role with id: 2041 not found."
}
```

#### Get Permissions By Role ID
- **POST** `/api/roles/:id/permissions`
##### Response
```json
[
  {
    "id": 4,
    "name": "Create Resource Level 3",
    "description": "Create Resource Level 3 - description",
    "status": "ACTIVE",
    "applicationId": 1
  },
  {
    "id": 1,
    "name": "Create Resource Level 1",
    "description": "Create Resource Level 1 - description",
    "status": "ACTIVE",
    "applicationId": 1
  },
  {
    "id": 2,
    "name": "Create Resource Level 2",
    "description": "Create Resource Level 2 - description",
    "status": "ACTIVE",
    "applicationId": 1
  }
]
```

#### Add Permissions
- **POST** `/api/roles/:id/addPermissions`
##### Request Body
```json
[1,2,3,4]
```
##### Response
```json
[
    {
        "id": 2,
        "name": "Create Resource Level 2",
        "description": "Create Resource Level 2 - description",
        "status": "ACTIVE",
        "applicationId": 1
    },
    {
        "id": 1,
        "name": "Create Resource Level 1",
        "description": "Create Resource Level 1 - description",
        "status": "ACTIVE",
        "applicationId": 1
    },
    {
        "id": 4,
        "name": "Create Resource Level 3",
        "description": "Create Resource Level 3 - description",
        "status": "ACTIVE",
        "applicationId": 1
    }
]
```


#### Remove Permissions
- **POST** `/api/roles/:id/removePermissions`
##### Request Body
```json
[2, 4]
```
##### Response
```json
[
  {
    "id": 2,
    "name": "Create Resource Level 2",
    "description": "Create Resource Level 2 - description",
    "status": "ACTIVE",
    "applicationId": 1
  },
  {
    "id": 4,
    "name": "Create Resource Level 3",
    "description": "Create Resource Level 3 - description",
    "status": "ACTIVE",
    "applicationId": 1
  }
]
```
### 🛂 Permission Management

| Method | Endpoint                            | Request       | Response                  | Status Code                   |
|--------|-------------------------------------|---------------|---------------------------|-------------------------------|
| POST   | /api/permissions                    | PermissionDTO | PermissionDTO             | 201 Created, 404 Not Found    |
| GET    | /api/permissions?applicationId={id} | —             | List&lt;PermissionDTO&gt; | 200 OK                        |
| GET    | /api/permissions/{id}               | —             | PermissionDTO             | 200 OK, 404 Not Found         |
| PUT    | /api/permissions/{id}               | PermissionDTO | PermissionDTO             | 200 OK, 404 Not Found         |
| DELETE | /api/permissions/{id}               | —             | —                         | 204 No Content, 404 Not Found |
| GET    | /api/permissions/{id}/roles         | —             | List&lt;RoleDTO&gt;       | 200 OK, 404 Not Found         |

#### Rules
- Permission `name` must be unique within the same Application.
- Names are **case-insensitive** (e.g., `permission_create_user` and `PERMISSION_CREATE_USER` are considered duplicates).
- 
#### Schemas
##### PermissionDTO
```json
{
  "id": 1073741824,
  "name": "string",
  "description": "string",
  "status": "ACTIVE",
  "applicationId": 1073741824
}
```

#### Create Permission
- **POST** `/api/permissions`
##### Request & Response
```json
{
  "id": null,
  "name": "string",
  "description": "string",
  "status": "ACTIVE",
  "applicationId": 5
}
```
##### 404 Not Found Response
```json
{
    "status": "NOT_FOUND",
    "title": "Create Permission",
    "details": "Application with id: 5 not found."
}
```

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

#### Endpoints

| Método | Endpoint          | Request          | Response | Status Code    |
|--------|-------------------|------------------|--|----------------|
| POST   | `/api/menus`      | `MenuEntryDTO` | `MenuEntryDTO` | 201 Created    |
| GET    | `/api/menus`      | —                | `List<MenuEntryDTO>` | 200 OK         |
| GET    | `/api/menus/{id}` | —                | `MenuEntryDTO` | 200 OK         |
| PUT    | `/api/menus/{id}` | `MenuEntryDTO` | `MenuEntryDTO` | 200 OK         |
| DELETE | `/api/menus/{id}` | —                | — | 204 No Content |

#### 🔹 Create Menu

- **POST** `/api/menus`

**📥 Request:**
```json
{
  "name": "string",
  "type": "MENU_PAGE",
  "position": 1073741824,
  "icon": "string",
  "status": "ACTIVE",
  "target": "string",
  "url": "string",
  "parentId": null,
  "applicationId": 1073741824,
  "resourceId": 1073741824
}
```

**📤 Response:**
```json
{
  "id": 1073741824,
  "name": "string",
  "type": "MENU_PAGE",
  "position": 1073741824,
  "icon": "string",
  "status": "ACTIVE",
  "target": "string",
  "url": "string",
  "parentId": null,
  "applicationId": 1073741824,
  "resourceId": 1073741824,
  "createdBy": "string",
  "createdDate": "string",
  "lastModifiedBy": "string",
  "lastModifiedDate": "string"
}
```

#### 🔹 List Menus

- **GET** `/api/menus`
- **Query Params**:
  - `applicationId` (optional): string
  - `name` (optional): string
  - `type` (optional): string

**📤 Response:**
```json
[
  {
    "id": 1073741824,
    "name": "string",
    "type": "MENU_PAGE",
    "position": 1073741824,
    "icon": "string",
    "status": "ACTIVE",
    "target": "string",
    "url": "string",
    "parentId": 1073741824,
    "applicationId": 1073741824,
    "resourceId": 1073741824,
    "createdBy": "string",
    "createdDate": "string",
    "lastModifiedBy": "string",
    "lastModifiedDate": "string"
  }
]
```

#### 🔹 Search Menu by ID

- **GET** `/api/menus/{id}`

**📤 Response:**
```json
{
  "id": 1073741824,
  "name": "string",
  "type": "MENU_PAGE",
  "position": 1073741824,
  "icon": "string",
  "status": "ACTIVE",
  "target": "string",
  "url": "string",
  "parentId": 1073741824,
  "applicationId": 1073741824,
  "resourceId": 1073741824,
  "createdBy": "string",
  "createdDate": "string",
  "lastModifiedBy": "string",
  "lastModifiedDate": "string"
}
```

#### 🔹 Update Menu

- **PUT** `/api/menus/{id}`

**📥 Request:**
```json
{
  "name": "string",
  "type": "MENU_PAGE",
  "position": 1073741824,
  "icon": "string",
  "status": "ACTIVE",
  "target": "string",
  "url": "string",
  "parentId": null
}
```

**📤 Response:** *(Same format as GET by ID)*

#### 🔹 Remove Menu

- **DELETE** `/api/menus/{id}`

**📤 Response:** `204 No Content`

### 🧱 Resource Management

#### Endpoints

| Método | Endpoint                                   | Request                  | Response        | Status Code    |
|--------|--------------------------------------------|--------------------------|-----------------|----------------|
| POST   | `/api/resources`                           | `ResourceDTO`            | `ResourceDTO`   | 201 Created    |
| GET    | `/api/resources`                           | —                        | `List<ResourceDTO>` | 200 OK         |
| GET    | `/api/resources/{id}`                      | —                        | `ResourceDTO`   | 200 OK         |
| PUT    | `/api/resources/{id}`                      | `ResourceDTO`            | `ResourceDTO`   | 200 OK         |
| DELETE | `/api/resources/{id}`                      | —                        | —               | 204 No Content |
| POST   | `/api/resources/{id}/custom-fields`        | `Map<String, ?>`         | —               | 204 No Content |
| POST   | `/api/resources/{id}/custom-fields/remove` | `List<String>`           | —               | 204 No Content |
| GET    | `/api/resources/{id}/custom-fields`        | —                        | `Map<String, ?>`| 200 OK         |
| POST   | `/api/resources/{id}/add-items`            | `List<ResourceItemDTO>`  | `ResourceDTO`   | 200 OK         |
| POST   | `/api/resources/{id}/remove-items`         | `List<Integer>`          | `ResourceDTO`   | 200 OK         |

#### 🔹 Create Resource

- **POST** `/api/resources`

**📥 Request:**
```json
{
  "name": "string",
  "type": "API",
  "status": "ACTIVE",
  "applicationId": 1073741824,
  "items": [
    {
      "id": 1073741824,
      "name": "string",
      "url": "string",
      "permissionId": 1073741824,
      "resourceId": 1073741824
    }
  ],
  "externalId": "string"
}
```

**📤 Response:**
```json
{
  "id": 1073741824,
  "name": "string",
  "type": "API",
  "status": "ACTIVE",
  "applicationId": 1073741824,
  "items": [
    {
      "id": 1073741824,
      "name": "string",
      "url": "string",
      "permissionId": 1073741824,
      "resourceId": 1073741824,
      "createdBy": "string",
      "createdDate": "string",
      "lastModifiedBy": "string",
      "lastModifiedDate": "string"
    }
  ],
  "externalId": "string",
  "createdBy": "string",
  "createdDate": "string",
  "lastModifiedBy": "string",
  "lastModifiedDate": "string"
}
```

#### 🔹 List Resources

- **GET** `/api/resources`
- **Query Params**:
  - `applicationId` (optional): string
  - `name` (optional): string
  - `type` (optional): string
  - `externalID` (optional): string

**📤 Response:**
```json
[
  {
    "id": 1073741824,
    "name": "string",
    "type": "API",
    "status": "ACTIVE",
    "applicationId": 1073741824,
    "items": [
      {
        "id": 1073741824,
        "name": "string",
        "url": "string",
        "permissionId": 1073741824,
        "resourceId": 1073741824,
        "createdBy": "string",
        "createdDate": "string",
        "lastModifiedBy": "string",
        "lastModifiedDate": "string"
      }
    ],
    "externalId": "string",
    "createdBy": "string",
    "createdDate": "string",
    "lastModifiedBy": "string",
    "lastModifiedDate": "string"
  }
]
```

#### 🔹 Search Resource by ID

- **GET** `/api/resources/{id}`

**📤 Response:**
```json
{
  "id": 1073741824,
  "name": "string",
  "type": "API",
  "status": "ACTIVE",
  "applicationId": 1073741824,
  "items": [
    {
      "id": 1073741824,
      "name": "string",
      "url": "string",
      "permissionId": 1073741824,
      "resourceId": 1073741824,
      "createdBy": "string",
      "createdDate": "string",
      "lastModifiedBy": "string",
      "lastModifiedDate": "string"
    }
  ],
  "externalId": "string",
  "createdBy": "string",
  "createdDate": "string",
  "lastModifiedBy": "string",
  "lastModifiedDate": "string"
}
```

#### 🔹 Update Resource

- **PUT** `/api/resources/{id}`

**📥 Request:**
```json
{
  "name": "string",
  "type": "API",
  "status": "ACTIVE",
  "applicationId": 1073741824,
  "externalId": "string"
}
```

**📤 Response:** *(Same format as GET by ID)*

#### 🔹 Remove Resource

- **DELETE** `/api/resources/{id}`

**📤 Response:** `204 No Content`

---

#### 🔹 Resource Custom Fields

- **GET** `/api/resources/{id}/custom-fields`

**📤 Response:**
```json
{
  "field1": "value1",
  "field2": 69,
  "field3": {
    "field4": "value4"
  }
}
```

#### 🔹 Resource Add Custom Fields

- **POST** `/api/resources/{id}/custom-fields`

**📥 Request:**
```json
{
  "field1": "value1",
  "field2": "value2"
}
```
**📤 Response:** `204 No Content`

#### 🔹 Resource Remove Custom Fields

- **POST** `/api/resources/{id}/custom-fields/remove`

**📥 Request:**
```json
["field1", "field2"]
```
**📤 Response:** `204 No Content`

#### 🔹 Resource Add Items

- **POST** `/api/resources/{id}/add-items`

**📥 Request:**
```json
[
  {
    "name": "string",
    "url": "string",
    "permissionId": 1073741824,
    "resourceId": 1073741824
  }
]
```
**📤 Response:**
```json
{
  "id": 1073741824,
  "name": "string",
  "type": "API",
  "status": "ACTIVE",
  "applicationId": 1073741824,
  "items": [
    {
      "id": 1073741824,
      "name": "string",
      "url": "string",
      "permissionId": 1073741824,
      "resourceId": 1073741824,
      "createdBy": "string",
      "createdDate": "string",
      "lastModifiedBy": "string",
      "lastModifiedDate": "string"
    }
  ],
  "externalId": "string",
  "createdBy": "string",
  "createdDate": "string",
  "lastModifiedBy": "string",
  "lastModifiedDate": "string"
}
```

#### 🔹 Resource Remove Items

- **POST** `/api/resources/{id}/remove-items`

**📥 Request:**
```json
[69, 99]
```
**📤 Response:**
```json
{
  "id": 1073741824,
  "name": "string",
  "type": "API",
  "status": "ACTIVE",
  "applicationId": 1073741824,
  "items": [],
  "externalId": "string",
  "createdBy": "string",
  "createdDate": "string",
  "lastModifiedBy": "string",
  "lastModifiedDate": "string"
}
```


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

## 📊 Code Quality & Reports

This project integrates the following tools to ensure code quality and maintainability:

### ✅ Generate JaCoCo Code Coverage Report

Runs the tests and generates a coverage report:
```bash
# Run the following command
mvn clean verify
```

The coverage report will be available at:

```target/site/jacoco/index.html```


### 📚 Generate Javadoc Documentation

Generates the Javadoc API documentation:

```bash
# Run the following command
mvn javadoc:javadoc
```

The documentation can be found at:

```target/reports/apidocs/index.html```

The maven-javadoc-plugin is configured to attach a JAR with docs, also check:

```target/access-management-*-javadoc.jar```

### 🛡 Run OWASP Dependency Check

Checks for known vulnerabilities in the dependencies:

```bash
# Using Maven verify phase
mvn verify
```

```bash
# Invoke directly
mvn dependency-check:check
```

The generated report will be available at:

```target/reports/dependency-check-report.html```


---

## 🤝 Contributing

Contributions are welcome! Please open issues or submit pull requests with improvements or new features.

---

## 📄 License

This project is licensed under the Apache 2.0 License. See the [LICENSE](LICENSE) file for details.
