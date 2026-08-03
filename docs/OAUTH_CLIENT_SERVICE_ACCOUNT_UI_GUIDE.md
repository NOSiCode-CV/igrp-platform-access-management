# OAuth Clients & Service Accounts — UI Integration Guide

**Audience:** the Application Center team building the admin UI for OAuth client and service account management.

**Contract source:** all interaction goes through the TypeScript client at [`@igrp/platform-access-management-client-ts`](https://sonatype.nosi.cv/repository/igrp/@igrp%2fplatform-access-management-client-ts) (currently `0.2.0-beta.14`). Never call the API endpoints (`/api/clients/**`, `/api/service-accounts/**`) with a raw HTTP client — the SDK handles authentication, error mapping, and DTO typing consistently across services.

**Visual inspiration:** the Auth0 Application Management screens (screenshots supplied). This guide maps Auth0's field labels onto our API contract; where a field has no equivalent, that's called out explicitly so the UI doesn't render dead controls.

---

## 1. Domain model recap — before you build a screen

Two entities, tightly coupled:

- **OAuth Client** (`OAuthClientDTO`) — the OAuth2 credential. Has a `clientId`, `clientSecret`, `grantTypes`, `redirectUris`, token TTLs. Same concept as an "Application" in Auth0.
- **Service Account** (`ServiceAccountDTO`) — the platform identity a machine assumes when it presents client_credentials. **1:1 with an OAuth Client** (the field `oauthClientId` is required and unique). Holds the roles + direct permission grants that populate the token.

**Two separate CRUD surfaces**, but the UI should stitch them together:

| Scenario | Sequence |
|---|---|
| Interactive (browser) OAuth client (`authorization_code` + `refresh_token`) | Create OAuth Client only. No service account. |
| Machine-to-machine (`client_credentials`) | (a) Create OAuth Client with `grantTypes: ['client_credentials']`, capture the secret from the response; (b) Create Service Account referencing that client's `id`. |
| Delete an M2M identity | Delete the Service Account **first**, then delete the OAuth Client. The SDK does not cascade. |

**Secret handling.** `clientSecret` is only surfaced in the response body of `createOAuthClient`. The list and get endpoints never include it. The UI must:
- Show the secret on a one-shot success screen right after creation, with a "Copy" button.
- Warn the operator that leaving that screen loses the secret forever (the API has no rotate endpoint yet — see §6 Auth0 delta).

---

## 2. TypeScript client setup

```ts
import { AccessManagementClient } from '@igrp/platform-access-management-client-ts';

const client = new AccessManagementClient({
  baseUrl: process.env.IGRP_ACCESS_MANAGEMENT_API!,   // reused from .env.example
  // token: obtained from the App Center's user session; not shown here
});

// Two dedicated clients hang off the root:
client.oauthClients      // → OAuthClient in the SDK; class name kept for symmetry with backend
client.serviceAccounts   // → ServiceAccountClient
```

All the methods used in this guide return `ApiResponse<T>` — `{ status: number; data: T; headers: Record<string, string> }`. Non-2xx responses surface as thrown `ApiClientError` with a `problemDetail` field for the RFC 7807 body (message + `error` code).

---

## 3. Screen A — OAuth Clients list

**Purpose:** table of every client registered on this AM instance.

```ts
const { data: clients } = await client.oauthClients.listOAuthClients();
```

**Columns (proposal, mapped from `OAuthClientDTO`):**

| Column header | Field | Notes |
|---|---|---|
| Name | `clientName` | Human label. |
| Client ID | `clientId` | Copyable chip; monospace. |
| Application | `applicationCode` | Empty when the client isn't tied to an application. |
| Grants | `grantTypes[]` | Small chips (`authorization_code`, `client_credentials`, …). |
| Active | `active` | Toggle-shaped badge. Read-only in the list; edit in the settings tab. |
| Created | `createdAt` | Relative time. |
| Actions | — | `⋮` → Edit, Delete. |

No search endpoint on the backend yet — client-side filter over the returned array is fine at this scale.

---

## 4. Screen B — Create OAuth Client (multi-tab modal, Auth0-shaped)

**Reference:** the "Settings" screen in the Auth0 screenshots. Our contract is narrower than Auth0's, so several sections collapse or disappear entirely — the field-mapping tables below are the source of truth for what to render.

```ts
const { data: created } = await client.oauthClients.createOAuthClient({
  clientId:      'my-service',
  clientName:    'My Service',
  description:   '...',
  active:        true,
  applicationId: 42,              // optional
  grantTypes:    ['client_credentials'],
  scopes:        ['m2m'],
  redirectUris:  [],              // required only for authorization_code
  postLogoutRedirectUris:  [],              // required only for authorization_code
  accessTokenTtl:       3600,     // seconds; optional (server default applies)
  refreshTokenTtl:      2592000,
  authorizationCodeTtl: 300,
});

// created.clientSecret is populated ONLY here — show it once, then throw it away.
```

### 4.1 Tab: Basic Information (Auth0 §Basic Information)

| Label          | Our field | Type | Required | Notes |
|----------------|---|---|---|---|
| Name           | `clientName` | text | yes | Human label. |
| Client ID      | `clientId` | text | yes on create, immutable on edit | Machine key. Auth0 auto-generates it; ours accepts a caller-provided value. UI can either mirror Auth0 (auto-generate + let the operator override) or expose the field directly. On edit, render read-only. |
| Client Secret  | `clientSecret` | password | shown once after create | Never rendered on edit — no rotate endpoint yet. |
| Description    | `description` | textarea | no | Free text; no length limit enforced on the wire, but 500 chars is the entity column limit. |
| PKCE Required? | `requirePkce` | boolean | no | Whether to require PKCE for the client. |

### 4.2 Tab: Application Properties (Auth0 §Application Properties)

| Label                  | Our field | Notes                                                                                                                                      |
|------------------------|---|--------------------------------------------------------------------------------------------------------------------------------------------|
| Associated Application | `applicationId` | Combobox loading the current user applications options and map the application ID from the response as value and application name as label |
| Grant Types            | `grantTypes` | Auth0's "Regular Web / SPA / Native / M2M" collapses onto our grant-type multiselect. See §4.4.                                            | |

### 4.3 Tab: URIs (Auth0 §Application URIs) — only for `authorization_code` clients

| Label                 | Our field        | Notes |
|-----------------------|------------------|---|
| Allowed Callback URLs | `redirectUris[]` | Chip input. Required when `grantTypes` contains `authorization_code`. Byte-for-byte match with what the authorization request sends. |
| Allowed Logout URLs   | `postLogoutRedirectUris[]` | Chip input. Required when `grantTypes` contains `authorization_code`. |

Show this whole tab conditionally: hide it entirely for pure `client_credentials` clients.

### 4.4 Tab: Advanced → Grant Types (Auth0 §Advanced Settings → Grant Types)

| Label                                   | Our value |
|-----------------------------------------|---|
| Authorization Code                      | `authorization_code` |
| Refresh Token                           | `refresh_token` |
| Client Credentials                      | `client_credentials` |

Enforce these UX rules client-side (mirror the Auth0 warnings):
- `refresh_token` requires `authorization_code` in the same set — refresh alone doesn't make sense.
- `client_credentials` should not be combined with `authorization_code` in the same client — spin up a separate M2M client instead.

### 4.5 Tab: Token Lifetimes (Auth0 §ID Token / Refresh Token Expiration)

All three are seconds and optional (server defaults apply when omitted).

| Label                          | Our field | Server default (verify with backend at UI wire time) |
|--------------------------------|---|---|
| Access Token Lifetime          | `accessTokenTtl` | Ask the backend team for the current default; the field is required on the DTO type but the backend accepts null. |
| Maximum Refresh Token Lifetime | `refreshTokenTtl` | Same. |
| Authorization Code TTL         | `authorizationCodeTtl` | Auth0 doesn't expose this; we do. Add it as a "Advanced" input, defaults to 300s. |

---

## 5. Screen C — Edit OAuth Client Settings

Same layout as §4, minus:

- **Client ID** is read-only (the DTO accepts it in `OAuthClientRequestDTO`, but changing it detaches every token ever issued — the backend permits it, the UI should refuse it).
- **Client Secret** is not shown. No rotate flow (see §6).

```ts
await client.oauthClients.updateOAuthClient(clientRow.id, {
  clientId:      clientRow.clientId,   // must echo the current value
  clientName:    form.clientName,
  description:   form.description,
  active:        form.active,
  requirePkce:        form.requirePkce,
  applicationId: form.applicationId,
  grantTypes:    form.grantTypes,
  scopes:        form.scopes,
  redirectUris:  form.redirectUris,
  postLogoutRedirectUris: form.postLogoutRedirectUris,
  accessTokenTtl:       form.accessTokenTtl,
  refreshTokenTtl:      form.refreshTokenTtl,
  authorizationCodeTtl: form.authorizationCodeTtl,
});
```

**Warning banner while editing an M2M client** ("This client is used by service account `<name>`. Disabling it will break token issuance for that machine.") — resolve the reverse link by scanning the service-account list for `oauthClientId === clientRow.id`.

---

## 6. Screen D — Danger Zone (Auth0 §Danger Zone)

Auth0 offers two actions; we offer one.

| Action             | Our SDK call | Notes |
|--------------------|---|---|
| Delete this client | `client.oauthClients.deleteOAuthClient(id)` | Requires all linked service accounts to be deleted first (409 otherwise). Show a two-step confirmation dialog and warn about tokens becoming un-verifiable at introspection. |

---

## 7. Screen E — Service Accounts list

```ts
const { data: accounts } = await client.serviceAccounts.listServiceAccounts();
```

**Columns:**

| Column header | Field | Notes |
|---|---|---|
| Name | `name` | |
| Client ID | `clientId` | Denormalised — same as looking up the linked OAuth client's `clientId`. |
| Application | `applicationCode` | Optional. |
| Roles | `roleCodes[]` | Chip cluster. |
| Direct permissions | `permissionNames[]` | Chip cluster, small font. Explain in a tooltip: "Bypass the role layer — added to the effective set on top of role-inherited permissions." |
| Active | `active` | Same visual as OAuth client. |
| Created | `createdAt` | |
| Actions | — | Edit, Delete. |

---

## 8. Screen F — Create Service Account

**Prerequisite step in the wizard**: the operator must pick an existing OAuth client. Filter the client list to those with `grantTypes` including `client_credentials` and not yet used by any service account (compute the "already used" set from `listServiceAccounts()`'s `oauthClientId` values).

```ts
const { data: created } = await client.serviceAccounts.createServiceAccount({
  name:          'my-service-sa',
  description:   '...',
  active:        true,
  oauthClientId: pickedOAuthClient.id,   // required, 1:1 unique
  applicationId: 42,                     // optional
  roleIds:       [10, 11],
  permissionIds: [301, 302],
});
```

**Fields (there is no Auth0 equivalent — this is our own concept):**

| Label | Field | Notes                                                                                                                                                                                      |
|---|---|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Name | `name` | Human label, unique per deployment.                                                                                                                                                        |
| Description | `description` | Optional free text.                                                                                                                                                                        |
| Linked OAuth Client | `oauthClientId` | Autocomplete over the filtered client list. Show the picked client's `clientId` + `applicationCode` inline for confirmation.                                                               |
| Application | `applicationId` | Optional; separate from the OAuth client's application FK. Backend uses this first when stamping the `application_code` claim on issued tokens, then falls back to the OAuth client's app. |
| Roles | `roleIds[]` | Multiselect over `/api/departments/.../roles`. Store ids, display names with code between parentisis. e.g.: `Role A (ROLE_A)`.                                                             |
| Direct permissions | `permissionIds[]` | Multiselect over the permission catalog. Show a warning ("bypasses role scoping") when non-empty.                                                                                          |
| Active | `active` | Default true.                                                                                                                                                                              |

The creation response does **not** include a secret — the secret was surfaced when the OAuth client was created (§4).

---

## 9. Screen G — Edit Service Account

Same shape as §8, but pay attention to the replacement semantics:

```ts
// The full desired set — omitted ids are UNASSIGNED.
await client.serviceAccounts.updateServiceAccount(row.id, {
  name:          form.name,
  description:   form.description,
  active:        form.active,
  oauthClientId: row.oauthClientId,   // immutable in practice — reject changes
  applicationId: form.applicationId,
  roleIds:       form.roleIds,        // full set, not a delta
  permissionIds: form.permissionIds,  // full set, not a delta
});
```

**UI rules:**

- `oauthClientId` should be read-only in the edit form (the DTO accepts a change, but re-linking a service account to a different client is not a meaningful operation — treat the current UI as create-only for that field).
- `roleIds` / `permissionIds` are **replacement sets**. If the operator unchecks a role, the update payload must omit its id — do not treat the multiselect as "add these" or you'll silently retain removed roles. Confirm this behaviour in a form-level hint.
- Toggling `active=false` is the recommended "temporarily disable this machine" flow — cheaper than deleting.

---

## 10. Auth0 → iGRP AM field mapping (consolidated)

Quick cheatsheet for the UI author cross-referencing an Auth0 screenshot.

| Auth0 concept | Our contract | Where |
|---|---|---|
| Application | `OAuthClientDTO` | `client.oauthClients.*` |
| Name | `clientName` | Basic Info |
| Domain | — | Hide row |
| Client ID | `clientId` | Basic Info; immutable on edit |
| Client Secret | `clientSecret` | Response of `createOAuthClient` only |
| Description | `description` | Basic Info |
| Application Ownership | — | Hide row |
| Application Type | `grantTypes` | Advanced → Grant Types |
| Application Logo | — | Hide row |
| Application Login URI | — | Hide row |
| Allowed Callback URLs | `redirectUris` | URIs tab |
| Allowed Logout URLs | `redirectUris` (merged) | URIs tab |
| Allowed Web Origins / CORS | — | Hide rows |
| ID Token Lifetime | — | Hide |
| Access Token Lifetime | `accessTokenTtl` | Token Lifetimes |
| Refresh Token Lifetime | `refreshTokenTtl` | Token Lifetimes |
| Authorization Code TTL (Auth0 doesn't expose) | `authorizationCodeTtl` | Token Lifetimes |
| Refresh Token Rotation toggle | — | Hide (always on) |
| Grant Types multiselect | `grantTypes` | Advanced |
| Back-Channel Logout / Session Transfer / Sender-Constraining / PAR / JAR / MRRT / Social | — | Hide entire sections |
| Delete application | `deleteOAuthClient` | Danger Zone |
| Rotate Secret | **not supported yet** | Hide or disable |
| — (no Auth0 equivalent) | `ServiceAccountDTO` + `client.serviceAccounts.*` | Separate top-level page |

---

## 11. Error handling

All SDK calls throw `ApiClientError` on non-2xx. The typed body is `problemDetail` (RFC 7807). Discriminate on `problemDetail.error`:

| `problemDetail.error` | HTTP | Typical cause | UI response |
|---|---|---|---|
| `IGRP_AUTH_OAUTH_CLIENT_ID_ALREADY_EXISTS` | 409 | `clientId` collision on create | Field-level error under Client ID. |
| `IGRP_AUTH_OAUTH_CLIENT_HAS_SERVICE_ACCOUNT` | 409 | Deleting an OAuth client that a service account still links | Modal: "Delete the service account `<name>` first." |
| `IGRP_AUTH_SERVICE_ACCOUNT_OAUTH_CLIENT_UNIQUE` | 409 | Creating a service account against an OAuth client that's already linked | Force re-picking a client. |
| `OUT_OF_SCOPE` | 403 | Non-superadmin acting outside their department subtree (relevant if AM's scope enforcement is turned on in your deployment) | Toast: "You don't have permission to manage this department." |
| generic 400 with validation errors | 400 | Field validation | Attach errors to the corresponding form fields via `problemDetail.errors` map. |
| 401 | 401 | Session expired | Redirect to login. |
| 5xx | 5xx | Server error | Generic toast, log to Sentry. |

---

## 12. What we're not shipping (call these out to the UX author)

So the UI doesn't quietly render dead controls that will need to be reverted later:

- **No secret rotation** for OAuth clients (backend follow-up).
- **No per-tenant tenancy** — Domain, Application Ownership are not modelled.
- **No back-channel logout, mTLS, DPoP, PAR, JAR, MRRT, Session Transfer, Device Binding** — all of Auth0's enterprise / add-on rows should be omitted.
- **No client-level CORS or Web Origins** — CORS is a server-side deployment config.
- **No search / pagination** on either list endpoint — client-side filter for now.
- **No bulk operations** on service accounts (no bulk-delete, no bulk-role-assign).
- **Role assignment and permission grants use replacement semantics** — the UI must send the full desired set on every update.

---

## 13. See also

- SDK source (TS): [`packages/client/src/client/oauth-client.ts`](../../../frontend/packages/packages/client/src/client/oauth-client.ts), [`service-account-client.ts`](../../../frontend/packages/packages/client/src/client/service-account-client.ts)
- SDK types: [`packages/client/src/types/index.ts`](../../../frontend/packages/packages/client/src/types/index.ts) — search for `OAuthClientDTO`, `OAuthClientRequestDTO`, `ServiceAccountDTO`, `ServiceAccountRequestDTO`, `OAuthGrantType`.
- Backend controllers: [`OAuthClientController.java`](../src/main/java/cv/igrp/platform/access_management/oauth_server/interfaces/rest/OAuthClientController.java), [`ServiceAccountController.java`](../src/main/java/cv/igrp/platform/access_management/oauth_server/interfaces/rest/ServiceAccountController.java) — authoritative contract, including the RFC 7807 error codes.
- Default service account bootstrap (why the platform always has one M2M identity out of the box): [`DefaultServiceAccountBootstrap.java`](../src/main/java/cv/igrp/platform/access_management/oauth_server/infrastructure/bootstrap/DefaultServiceAccountBootstrap.java) and the corresponding `IGRP_OAUTH_DEFAULT_SERVICE_ACCOUNT_*` env vars in application-center's [`.env.example`](../../../frontend/application-center/.env.example).
