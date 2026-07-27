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

## 4. Environment variables

The sync is gated and configured entirely through environment variables so the same Next.js image ships to every environment.

| Variable                                | Required                                     | Default                            | Purpose |
|-----------------------------------------|----------------------------------------------|------------------------------------|---|
| `IGRP_ACCESS_MANAGEMENT_BASE_URL`       | **yes** (when sync is enabled)               | —                                  | Base URL of the IAM API. Example: `https://api-demoigrp.nosi.cv/igrp-access-management`. Both `/oauth2/token` and `/api/m2m/*` hang off this. |
| `IGRP_M2M_CLIENT_ID`                    | **yes** (when sync is enabled)               | —                                  | OAuth2 `client_credentials` client id. Never checked into git. |
| `IGRP_M2M_CLIENT_SECRET`                | **yes** (when sync is enabled)               | —                                  | OAuth2 `client_credentials` client secret. Load from your secrets manager. |
| `IGRP_M2M_SCOPE`                        | no                                           | `m2m`                              | OAuth2 scope. Override if your IAM instance uses a different name. |
| `IGRP_PERMISSIONS_SYNC_ENABLED`         | no                                           | `false` in `development`, `true` elsewhere | Master switch. When `false`, `syncPermissionsFromStudio()` returns immediately without contacting IAM. |
| `IGRP_PERMISSIONS_SYNC_ON_STARTUP`      | no                                           | `false`                            | If `true`, sync runs on server boot (via the Next.js `instrumentation.ts` hook). Otherwise, sync only runs when you invoke the CLI script explicitly. |
| `IGRP_PERMISSIONS_SYNC_FILE`            | no                                           | `.igrpstudio/permissions.json`     | Location of the source-of-truth file, relative to the process cwd. Rarely overridden. |
| `IGRP_PERMISSIONS_SYNC_SERVICE_ID`      | no                                           | none                               | Value for the informational `X-Machine-Service-ID` header. Handy on IAM logs to identify which service triggered a sync when multiple services share one OAuth client. |
| `IGRP_PERMISSIONS_SYNC_FAIL_ON_ERROR`   | no                                           | `false` in `production`, `true` in `development` | Whether a sync failure aborts server startup. Prod defaults to non-fatal to prevent an IAM outage from wedging the app; dev defaults to fatal so misconfiguration surfaces immediately. |

**Rationale for `IGRP_PERMISSIONS_SYNC_ON_STARTUP=false` default.** Running sync at every container start on a horizontally-scaled deployment means N replicas race to hit `/api/m2m/sync/permissions` for the same catalog. The endpoint is idempotent, but the noise is undesirable. Prefer a one-shot CI/CD step that runs `pnpm igrp:sync-permissions` after image build and before rollout.

---

## 5. Integration pattern A — CI/CD script (recommended)

Run the sync once per deploy, not once per container start.

### 5.1 Add the CLI entry point

```ts
// scripts/sync-permissions.ts
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { M2MClient } from '@igrp/platform-access-management-client-ts';
import { toPermissionDTOs, type PermissionsJson } from '../src/lib/igrp/permissions-sync';

async function main(): Promise<void> {
  const enabled = process.env.IGRP_PERMISSIONS_SYNC_ENABLED === 'true';
  if (!enabled) {
    console.log('[igrp-sync] IGRP_PERMISSIONS_SYNC_ENABLED != true — skipping.');
    return;
  }

  const baseUrl      = required('IGRP_ACCESS_MANAGEMENT_BASE_URL');
  const clientId     = required('IGRP_M2M_CLIENT_ID');
  const clientSecret = required('IGRP_M2M_CLIENT_SECRET');
  const scope        = process.env.IGRP_M2M_SCOPE ?? 'm2m';
  const serviceId    = process.env.IGRP_PERMISSIONS_SYNC_SERVICE_ID;
  const filePath     = resolve(
    process.cwd(),
    process.env.IGRP_PERMISSIONS_SYNC_FILE ?? '.igrpstudio/permissions.json',
  );

  const raw = readFileSync(filePath, 'utf-8');
  const source = JSON.parse(raw) as PermissionsJson;
  const permissions = toPermissionDTOs(source);
  console.log(`[igrp-sync] Syncing ${permissions.length} permissions from ${filePath}`);

  const m2m = new M2MClient(
    { baseUrl },
    { clientId, clientSecret, scope, serviceId },
  );

  const result = await m2m.syncPermissions(permissions);
  if (result.status !== 204) {
    throw new Error(`Unexpected status ${result.status} from syncPermissions`);
  }
  console.log(`[igrp-sync] OK — ${permissions.length} permissions synced.`);
}

function required(name: string): string {
  const v = process.env[name];
  if (!v || v.trim() === '') throw new Error(`Missing required env var: ${name}`);
  return v;
}

main().catch((err) => {
  const fatal = (process.env.IGRP_PERMISSIONS_SYNC_FAIL_ON_ERROR ?? 'true') === 'true';
  console.error('[igrp-sync] FAILED', err);
  if (fatal) process.exit(1);
});
```

### 5.2 Wire it into `package.json`

```json
{
  "scripts": {
    "igrp:sync-permissions": "tsx scripts/sync-permissions.ts"
  }
}
```

### 5.3 Add it to your pipeline

```yaml
# .github/workflows/deploy.yml (or .gitlab-ci.yml, etc.)
- name: Sync iGRP permissions
  env:
    IGRP_PERMISSIONS_SYNC_ENABLED: 'true'
    IGRP_ACCESS_MANAGEMENT_BASE_URL: ${{ vars.IGRP_ACCESS_MANAGEMENT_BASE_URL }}
    IGRP_M2M_CLIENT_ID: ${{ secrets.IGRP_M2M_CLIENT_ID }}
    IGRP_M2M_CLIENT_SECRET: ${{ secrets.IGRP_M2M_CLIENT_SECRET }}
    IGRP_PERMISSIONS_SYNC_SERVICE_ID: ${{ github.repository }}
  run: pnpm igrp:sync-permissions
```

Run this **after** the image build and **before** the rollout starts. If the sync fails, fail the deploy — a mismatched permission catalog is worse than a rolled-back release.

---

## 6. Integration pattern B — Next.js boot hook (opt-in)

If you can't add a CI/CD step (e.g. edge deployment where you don't own the pipeline), Next.js's `instrumentation.ts` hook fires exactly once per process on server start.

```ts
// instrumentation.ts (in project root or /src)
export async function register(): Promise<void> {
  if (process.env.NEXT_RUNTIME !== 'nodejs') return;
  if (process.env.IGRP_PERMISSIONS_SYNC_ON_STARTUP !== 'true') return;

  const { syncPermissionsFromStudio } = await import('./src/lib/igrp/permissions-sync-runner');
  try {
    await syncPermissionsFromStudio();
  } catch (err) {
    const fatal = (process.env.IGRP_PERMISSIONS_SYNC_FAIL_ON_ERROR ?? 'false') === 'true';
    // eslint-disable-next-line no-console
    console.error('[igrp-sync] Boot-time sync failed', err);
    if (fatal) process.exit(1);
  }
}
```

```ts
// src/lib/igrp/permissions-sync-runner.ts
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { M2MClient } from '@igrp/platform-access-management-client-ts';
import { toPermissionDTOs, type PermissionsJson } from './permissions-sync';

export async function syncPermissionsFromStudio(): Promise<void> {
  const baseUrl      = mustEnv('IGRP_ACCESS_MANAGEMENT_BASE_URL');
  const clientId     = mustEnv('IGRP_M2M_CLIENT_ID');
  const clientSecret = mustEnv('IGRP_M2M_CLIENT_SECRET');
  const scope        = process.env.IGRP_M2M_SCOPE ?? 'm2m';
  const serviceId    = process.env.IGRP_PERMISSIONS_SYNC_SERVICE_ID;
  const filePath     = resolve(
    process.cwd(),
    process.env.IGRP_PERMISSIONS_SYNC_FILE ?? '.igrpstudio/permissions.json',
  );

  const source = JSON.parse(readFileSync(filePath, 'utf-8')) as PermissionsJson;
  const permissions = toPermissionDTOs(source);

  const m2m = new M2MClient(
    { baseUrl },
    { clientId, clientSecret, scope, serviceId },
  );

  await m2m.syncPermissions(permissions);
}

function mustEnv(name: string): string {
  const v = process.env[name];
  if (!v) throw new Error(`Missing required env var: ${name}`);
  return v;
}
```

**Warnings for boot-hook mode:**

1. **Race across replicas.** If N pods start at once, they all hit the sync endpoint simultaneously. The endpoint is idempotent so no data corruption — just noise in IAM logs. Consider a `IGRP_PERMISSIONS_SYNC_ON_STARTUP=true` only on pod index 0 (leader-election) or on cron.
2. **Cold-start latency.** Every request served during the sync will wait behind it. For a large catalog (~200+ permissions) this is 1–2 s of added TTFB on the first request per pod.
3. **IAM downtime.** If the IAM API is unreachable during boot, the Next.js pod fails to serve. Default `IGRP_PERMISSIONS_SYNC_FAIL_ON_ERROR=false` keeps the pod alive; the sync just logs an error and moves on. Retry manually via the CLI script when IAM is back.

---

## 7. Testing the mapping

```ts
// scripts/sync-permissions.test.ts
import { describe, it, expect } from 'vitest';
import { Status } from '@igrp/platform-access-management-client-ts';
import { toPermissionDTOs } from '../src/lib/igrp/permissions-sync';

describe('toPermissionDTOs', () => {
  it('maps enabled=true to Status.ACTIVE and drops id + label', () => {
    const dtos = toPermissionDTOs({
      permissions: [
        {
          id: 'perm_01HZX111',
          name: 'inss.invoice_list.delete',
          label: 'Delete Invoice',
          description: 'Permite eliminar faturas',
          enabled: true,
        },
      ],
    });
    expect(dtos).toHaveLength(1);
    expect(dtos[0]).toMatchObject({
      name: 'inss.invoice_list.delete',
      description: 'Permite eliminar faturas',
      status: Status.ACTIVE,
    });
    // id and label are not part of the outgoing payload
    expect((dtos[0] as any).label).toBeUndefined();
  });

  it('maps enabled=false to Status.INACTIVE', () => {
    const dtos = toPermissionDTOs({
      permissions: [{ id: 'x', name: 'x.y.z', enabled: false }],
    });
    expect(dtos[0].status).toBe(Status.INACTIVE);
  });

  it('coerces missing description to null (never omits the key)', () => {
    const dtos = toPermissionDTOs({
      permissions: [{ id: 'x', name: 'x.y.z', enabled: true }],
    });
    expect(dtos[0].description).toBeNull();
  });
});
```

---

## 8. Troubleshooting

| Symptom                                                          | Likely cause                                                                 | Fix |
|------------------------------------------------------------------|------------------------------------------------------------------------------|---|
| `401 Unauthorized` from `/oauth2/token`                          | Wrong `clientId` / `clientSecret`, or the client is disabled in IAM.         | Re-check secrets; hit `POST /oauth2/token` manually with curl to isolate. |
| `403 Forbidden` from `/api/m2m/sync/permissions`                 | Client is authenticated but its service account lacks the `igrp.m2m.sync` permission (or the equivalent your IAM uses). | Grant the missing permission via the IAM admin UI. |
| `400 Bad Request` naming a field                                 | A permission `name` violates `^[A-Za-z0-9._-]+$` or exceeds 255 chars.       | Fix in `.igrpstudio/permissions.json` and re-sync. |
| Sync appears to succeed but the permission never shows up in IAM | Old cached SDK version pointing at the wrong `baseUrl`; wrong environment.  | `console.log(baseUrl)` in the runner; ensure the deploy set `IGRP_ACCESS_MANAGEMENT_BASE_URL` correctly. |
| Retired a permission in the JSON but IAM still lists it          | **Expected.** Sync is upsert-only; retire via IAM admin UI or a separate DELETE. | Manual cleanup for now; deferred to `/api/m2m/sync/permissions?prune=true` in a future release. |
| Cold-start latency spike after enabling boot-hook mode           | See §6 warning 2.                                                            | Switch to CI/CD mode (§5). |

---

## 9. See also

- [`IGRP_PERMISSIONS_INTEGRATION_GUIDE.md`](./IGRP_PERMISSIONS_INTEGRATION_GUIDE.md) — the equivalent guide for Spring Boot target projects (uses `AuthorizationSyncRunner` + `@IgrpPermission` annotation processor instead of a JSON file).
- [`IAM_SYNCHRONIZATION.md`](./IAM_SYNCHRONIZATION.md) — deeper reference on the M2M sync endpoints (resources, applications, menus in addition to permissions).
- [`../src/main/java/cv/igrp/platform/access_management/m2m/interfaces/rest/M2MController.java`](../src/main/java/cv/igrp/platform/access_management/m2m/interfaces/rest/M2MController.java) — the authoritative endpoint contract.
- Backend command handler: [`SyncPermissionsCommandHandler`](../src/main/java/cv/igrp/platform/access_management/m2m/application/commands/SyncPermissionsCommandHandler.java) and service: `PermissionSyncService` (`synchronizePermissions(list, false)` — the `false` flag disables pruning).
- SDK source (TypeScript): `access-management/frontend/packages/packages/client/src/client/m2m-client.ts`.
