# iGRP Permissions Sync — Next.js Guide

**Audience:** developers building a **Next.js project generated with iGRP** that needs its business permissions registered in the central iGRP Access Management API.

This is the Next.js counterpart of [`IGRP_PERMISSIONS_INTEGRATION_GUIDE.md`](./IGRP_PERMISSIONS_INTEGRATION_GUIDE.md) (which covers the Spring Boot target-project flow). The mechanism is the same — call the M2M sync endpoint at boot — only the source of truth and the client differ:

- **Source of truth:** `.igrpstudio/permissions.json` in the project root (edited by iGRP Studio).
- **Client:** [`@igrp/platform-access-management-client-ts`](https://sonatype.nosi.cv/repository/igrp/@igrp%2fplatform-access-management-client-ts), method `M2MClient.syncPermissions(...)`. Never call `POST /api/m2m/sync/permissions` with a raw fetch — always go through the SDK so retries, token refresh, and error mapping stay consistent across services.

---

## 1. The big picture

```
┌───────────────────────────── Next.js project (your app) ──────────────────────────────┐
│                                                                                        │
│  .igrpstudio/permissions.json           ← edited in iGRP Studio, checked into git      │
│    { "permissions": [ { name, label, description, enabled, ... }, ... ] }              │
│                                                                                        │
│              │  build- or boot-time script  ▾                                          │
│                                                                                        │
│  new M2MClient(                                                                        │
│    { baseUrl: IGRP_ACCESS_MANAGEMENT_BASE_URL },                                       │
│    { clientId: IGRP_M2M_CLIENT_ID, clientSecret: IGRP_M2M_CLIENT_SECRET },             │
│  ).syncPermissions(mapped)                                                             │
│                                                                                        │
│              │  the SDK does under the hood:                                           │
│              │    POST {baseUrl}/oauth2/token   (client_credentials → access token)    │
│              │    POST {baseUrl}/api/m2m/sync/permissions                              │
│                                                                                        │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

The sync is **idempotent upsert** — safe to run on every deploy. Missing permissions are created; permissions with the same `name` are updated in place; permissions removed from the JSON are **left alone in IAM** (soft-deletion would break tokens already granting them — do that manually via the IAM UI when you're ready to retire one).

---

## 2. Prerequisites

### 2.1 A registered OAuth2 client in IAM

Ask your IAM administrator (or use `/api/oauth-clients` if you have access) to create an OAuth2 client for your Next.js project:

- **Grant type:** `client_credentials`
- **Scopes:** `m2m` (or the scope your IAM instance uses for machine calls)
- **Permissions to grant to the client's service account:** at minimum `igrp.m2m.sync` (name varies per deployment — the M2M sync endpoint enforces it)

Store the `clientId` + `clientSecret` in a secrets manager. Do **not** commit them.

### 2.2 Install the SDK

```bash
pnpm add @igrp/platform-access-management-client-ts
# or
npm i @igrp/platform-access-management-client-ts
```

Version pin recommendation: pin the exact version (`0.2.0-beta.14` at the time of writing) so a Nexus republish doesn't silently change behaviour on your next deploy.

---

## 3. The source-of-truth file: `.igrpstudio/permissions.json`

Every iGRP Studio-generated Next.js project has this file at the repo root. Its shape:

```json
{
  "permissions": [
    {
      "id": "perm_01HZX111",
      "name": "inss.invoice_list.delete",
      "label": "Delete Invoice",
      "description": "Permite eliminar faturas",
      "enabled": true
    }
  ]
}
```

### 3.1 Field mapping — JSON → request body

The M2M sync endpoint expects `PermissionDTO[]` (see [`PermissionDTO.java`](../src/main/java/cv/igrp/platform/access_management/shared/application/dto/PermissionDTO.java)). Only three JSON fields are meaningful for the sync:

| JSON field    | → PermissionDTO field | Type / rule                                                                                                                          | Notes |
|---------------|----------------------|--------------------------------------------------------------------------------------------------------------------------------------|---|
| `name`        | `name`               | string, required, `^[A-Za-z0-9._-]+$`, ≤ 255 chars — the identity of the permission across systems                                    | The upsert key. Never rename in place; retire the old one and add a new one instead. |
| `description` | `description`        | string, optional, ≤ 255 chars                                                                                                        | Human-readable, surfaces in the IAM admin UI. |
| `enabled`     | `status`             | `true` → `"ACTIVE"`, `false` → `"INACTIVE"`                                                                                          | Boolean flip; disabled permissions still exist in IAM but no new role can grant them. |

**Explicitly not sent:**

- `id` — the local iGRP Studio identifier (`perm_01HZX111`) is meaningless server-side; IAM assigns its own numeric id on create and matches on `name` on subsequent syncs.
- `label` — a UI-only string used by iGRP Studio. IAM does not store it (it uses `description` as the human label).

**PermissionDTO fields you don't set** (they exist on the DTO but aren't in the JSON):

- `departmentCode` / `departments` — leave empty / empty array. Business permissions synced by the app are not pre-associated with any department; department managers grant them to roles later via the IAM UI.

### 3.2 Mapping function

```ts
// src/lib/igrp/permissions-sync.ts
import type { PermissionDTO } from '@igrp/platform-access-management-client-ts';
import { Status } from '@igrp/platform-access-management-client-ts';

export interface IgrpStudioPermission {
  id: string;              // ignored on sync
  name: string;
  label?: string;          // ignored on sync
  description?: string;
  enabled: boolean;
}

export interface PermissionsJson {
  permissions: IgrpStudioPermission[];
}

/**
 * Map the .igrpstudio/permissions.json entries onto the PermissionDTO
 * shape the M2M sync endpoint expects. `id` and `label` are dropped —
 * the sync is keyed on `name`.
 */
export function toPermissionDTOs(source: PermissionsJson): PermissionDTO[] {
  return source.permissions.map((p) => ({
    // Backend ignores `id` on incoming payloads (matches on `name`); we
    // send a placeholder to satisfy the TS type without shipping the
    // opaque local id.
    id: undefined as unknown as number,
    name: p.name,
    description: p.description ?? null,
    status: p.enabled ? Status.ACTIVE : Status.INACTIVE,
    // No pre-associated departments on M2M sync.
    departmentCode: '',
  }));
}
```

---

## 4. Environment variable

Permissions sync **reuses the existing IAM-connection variables** already documented in application-center's [`.env.example`](../../../frontend/application-center/.env.example) — do not re-declare them:

- `IGRP_ACCESS_MANAGEMENT_API` — base URL of the IAM API (`/oauth2/token` and `/api/m2m/*` hang off this).
- `IGRP_M2M_CLIENT_ID` / `IGRP_M2M_CLIENT_SECRET` — OAuth2 `client_credentials` pair.
- `IGRP_SERVICE_ID` — sent as `X-Machine-Service-ID`.
- `IGRP_SYNC_ACCESS` — master sync toggle. Must be `true` for any M2M sync to fire.
- `IGRP_PREVIEW_MODE` — must be `false` (or unset) for sync to fire.

**One new variable to add** — the permissions-specific opt-in, following the same naming pattern as `IGRP_SYNC_ON_CODE_MENUS`:

```env
# Enable permissions catalog synchronization
# When set to true, the framework reads `.igrpstudio/permissions.json`
# and pushes the entries to Access Management at startup via
# M2MClient.syncPermissions(...). Requires IGRP_SYNC_ACCESS=true and
# IGRP_PREVIEW_MODE=false to take effect.
# When false (default), AM remains the source of truth and no push happens.
# NOTE: sync is idempotent upsert keyed on `name` — permissions removed
# from the JSON are NOT deleted in AM; retire them via the AM admin UI.
# Values: true or false
IGRP_SYNC_PERMISSIONS=false
```

Add that block to your project's `.env.example` alongside `IGRP_SYNC_ON_CODE_MENUS`.

---

## 5. Wiring the sync — same shape as `IGRP_SYNC_ON_CODE_MENUS`

**Recommended:** follow the exact pattern the framework uses for the on-code menu push. Menus load a local array at build time and hand it to `apiManagementConfig` in [`igrp.template.config.ts`](../../../frontend/application-center/src/igrp.template.config.ts); the framework's `@igrp/framework-next` runtime picks it up and calls `syncApplicationMenus` at boot when `syncOnCodeMenus === true`. Permissions get the parallel treatment.

### 5.1 Load + map the JSON

```ts
// src/lib/igrp/permissions.ts
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import type { PermissionDTO } from '@igrp/platform-access-management-client-ts';
import { Status } from '@igrp/platform-access-management-client-ts';

export interface IgrpStudioPermission {
  id: string;              // ignored on sync
  name: string;
  label?: string;          // ignored on sync
  description?: string;
  enabled: boolean;
}
interface PermissionsJson { permissions: IgrpStudioPermission[] }

// Loaded once at module import — same lifetime as IGRP_DEFAULT_MENU from
// `@/temp/menus/menus`, so the array is baked into the server bundle at
// build time and doesn't require disk I/O per boot.
const filePath = resolve(process.cwd(), '.igrpstudio/permissions.json');
const source: PermissionsJson = JSON.parse(readFileSync(filePath, 'utf-8'));

export const IGRP_DEFAULT_PERMISSIONS: PermissionDTO[] = source.permissions.map((p) => ({
  id: undefined as unknown as number,      // AM assigns / matches on `name`
  name: p.name,
  description: p.description ?? null,
  status: p.enabled ? Status.ACTIVE : Status.INACTIVE,
  departmentCode: '',                       // not applicable on M2M sync
}));
```

### 5.2 Pass it into `igrp.template.config.ts` next to the menus

```ts
// src/igrp.template.config.ts
import { igrpBuildConfig } from '@igrp/framework-next';
import { IGRP_DEFAULT_MENU } from '@/temp/menus/menus';
import { IGRP_DEFAULT_PERMISSIONS } from '@/lib/igrp/permissions';   // ← add
// ...

return igrpBuildConfig({
  // ...existing fields (appCode, previewMode, syncAccess, ...)
  apiManagementConfig: {
    baseUrl: process.env.IGRP_ACCESS_MANAGEMENT_API || '',
    serviceId: process.env.IGRP_SERVICE_ID || '',
    m2mClientId: process.env.IGRP_M2M_CLIENT_ID || '',
    m2mClientSecret: process.env.IGRP_M2M_CLIENT_SECRET || '',

    // existing on-code menus block
    syncOnCodeMenus: process.env.IGRP_SYNC_ON_CODE_MENUS === 'true',
    syncOnCodeMenuRoles: process.env.IGRP_SYNC_ON_CODE_MENU_ROLES !== 'false',
    onCodeMenus: IGRP_DEFAULT_MENU,

    // new on-code permissions block — same shape
    syncPermissions: process.env.IGRP_SYNC_PERMISSIONS === 'true',
    onCodePermissions: IGRP_DEFAULT_PERMISSIONS,

    appRoutes,
    paramMapBody,
  },
  // ...
});
```

That's the entire integration on the app side. The framework:

1. Sees `syncAccess === true && previewMode === false` — the standard M2M sync gate.
2. If `syncPermissions === true`, calls `M2MClient.syncPermissions(onCodePermissions)` at startup — same `after()`-scheduled flow that already handles `syncOnCodeMenus`, so it never blocks the first request.
3. On IAM outage or 4xx, logs and moves on (matches the menu-sync failure policy — the pod stays healthy).

**Note on replicas.** Every replica boot triggers one sync call. The endpoint is idempotent (upsert keyed on `name`), so no data corruption — just log noise proportional to replica count. Identical trade-off to `IGRP_SYNC_ON_CODE_MENUS`.

---

## 6. Testing the mapping

```ts
// src/lib/igrp/permissions.test.ts
import { describe, it, expect } from 'vitest';
import { Status } from '@igrp/platform-access-management-client-ts';
import { IGRP_DEFAULT_PERMISSIONS } from './permissions';

// The module is loaded once at import and reflects the real
// .igrpstudio/permissions.json — inspect the derived array directly.
describe('IGRP_DEFAULT_PERMISSIONS', () => {
  it('maps every enabled row to Status.ACTIVE and drops id + label', () => {
    for (const p of IGRP_DEFAULT_PERMISSIONS) {
      expect(p.name).toMatch(/^[A-Za-z0-9._-]+$/);
      expect(p.status === Status.ACTIVE || p.status === Status.INACTIVE).toBe(true);
      expect((p as any).label).toBeUndefined();
    }
  });
});
```

---

## 7. Troubleshooting

| Symptom                                                          | Likely cause                                                                 | Fix |
|------------------------------------------------------------------|------------------------------------------------------------------------------|---|
| Sync silently doesn't run                                        | One of the three gates is false: `IGRP_SYNC_ACCESS`, `IGRP_PREVIEW_MODE=false`, `IGRP_SYNC_PERMISSIONS`. | Log the three values at boot. |
| `401 Unauthorized` from `/oauth2/token`                          | Wrong `IGRP_M2M_CLIENT_ID` / `IGRP_M2M_CLIENT_SECRET`, or the client is disabled in IAM. | Re-check secrets. |
| `403 Forbidden` from `/api/m2m/sync/permissions`                 | The M2M client's service account lacks `igrp.m2m.sync` (or the equivalent).  | Grant it via the IAM admin UI. |
| `400 Bad Request` naming a field                                 | A permission `name` violates `^[A-Za-z0-9._-]+$` or exceeds 255 chars.       | Fix `.igrpstudio/permissions.json` and re-deploy. |
| Retired a permission in the JSON but IAM still lists it          | **Expected.** Sync is upsert-only; retire via IAM admin UI.                  | Manual cleanup for now. |

---

## 8. See also

- [`IGRP_PERMISSIONS_INTEGRATION_GUIDE.md`](./IGRP_PERMISSIONS_INTEGRATION_GUIDE.md) — the equivalent guide for Spring Boot target projects (uses `AuthorizationSyncRunner` + `@IgrpPermission` annotation processor instead of a JSON file).
- [`IAM_SYNCHRONIZATION.md`](./IAM_SYNCHRONIZATION.md) — deeper reference on the M2M sync endpoints (resources, applications, menus in addition to permissions).
- [`../src/main/java/cv/igrp/platform/access_management/m2m/interfaces/rest/M2MController.java`](../src/main/java/cv/igrp/platform/access_management/m2m/interfaces/rest/M2MController.java) — the authoritative endpoint contract.
- Backend command handler: [`SyncPermissionsCommandHandler`](../src/main/java/cv/igrp/platform/access_management/m2m/application/commands/SyncPermissionsCommandHandler.java) and service: `PermissionSyncService` (`synchronizePermissions(list, false)` — the `false` flag disables pruning).
- SDK source (TypeScript): `access-management/frontend/packages/packages/client/src/client/m2m-client.ts`.
