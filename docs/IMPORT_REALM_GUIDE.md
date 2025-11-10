# Importing the iGRP Realm into Keycloak (Version 26+)

This guide explains how to import the **iGRP realm** into **Keycloak version 26 or later** using the provided `igrp-realm.json` file.

> ⚠️ **Important Notice**
>
> The realm configuration assumes that **Keycloak is running on `http://localhost:8080`**.
>
> If your Keycloak host runs on a **different domain or port**, you **must update all occurrences** of `localhost:8080` in the JSON file to match your actual Keycloak host.
>
> Example:  
> If your Keycloak runs on `https://auth.mycompany.com`, replace:
> ```
> http://localhost:8080
> ```
> with:
> ```
> https://auth.mycompany.com
> ```
> Failure to update this will cause redirect URI mismatches and login failures.

---

## 1. Files and Requirements

### Required Files
- `igrp-realm.json` — the iGRP realm configuration file.

### Required Environment
- Keycloak **version 26 or higher**
- Java 17+
- Administrator access to Keycloak (via web console or command line)

---

## 2. Importing the Realm via the Admin Console

If you are starting from a fresh Keycloak instance or want to add this realm to an existing server:

1. **Open Keycloak Admin Console**

   Visit your Keycloak instance in a browser:
   ```
   http://localhost:8080
   ```
   (Replace with your actual host if different.)

2. **Log in as Administrator**

   Use your admin credentials that you configured during setup.

3. **Go to Realm Management**

   - In the top-left dropdown (next to the Keycloak logo), click it and select **"Create Realm"**.
   - Then click the **"Import"** button.

4. **Import the JSON**

   - Click **Browse** and select your `igrp-realm.json` file.
   - The JSON content will populate the realm configuration automatically.

5. **Confirm and Create**

   - Click **Create**.
   - Wait for Keycloak to finish importing the realm.

---

## 3. Importing via the Command Line (Optional)

If you prefer to import the realm automatically during Keycloak startup, you can do this via environment variables or CLI arguments.

### Option 1 — Using `KEYCLOAK_IMPORT` environment variable

Place your `igrp-realm.json` file inside a mounted folder accessible by Keycloak, and set:

```bash
export KEYCLOAK_IMPORT=/opt/keycloak/data/import/igrp-realm.json
```

Then start Keycloak with:

```bash
/opt/keycloak/bin/kc.sh start-dev
```

Keycloak will automatically import the realm at startup.

### Option 2 — Using CLI Parameters

Alternatively, you can specify the import directly when starting Keycloak:

```bash
/opt/keycloak/bin/kc.sh start-dev --import-realm
```

Ensure that:
- Your realm file is inside the `data/import/` directory (default import path).
- The filename matches the one you want to import (`igrp-realm.json`).

You can also customize the import directory by setting:
```bash
/opt/keycloak/bin/kc.sh start-dev --import-realm --import-path=/path/to/igrp-realm.json
```

---

## 4. Post-Import Steps

After importing, verify the realm setup:

1. **Switch to the iGRP Realm**
   - Use the dropdown at the top-left to select `igrp`.

2. **Check Clients**
   - Go to `Clients` → verify that all configured clients (e.g., `access-management`, `minio`, etc.) are present.
   - Ensure each client has the correct redirect URIs matching your host.

3. **Check Users**
   - Go to `Users` → verify that `superadmin` and other service accounts exist.
   - Default credentials may be defined in the import file (e.g., username `superadmin`, password `superadmin`).

4. **Check Roles and Mappers**
   - Under `Clients` → select a client → `Mappers` tab → verify that protocol mappers are correctly loaded.

---

## 5. Updating the Host in the JSON File

If your host is not `localhost:8080`, you must edit all URLs in the file that reference it.

### Quick Command-Line Replacement (Linux/Mac)
You can run:
```bash
sed -i 's|http://localhost:8080|https://your-host.com|g' igrp-realm.json
```

For Windows PowerShell:
```powershell
(Get-Content igrp-realm.json) -replace 'http://localhost:8080','https://your-host.com' | Set-Content igrp-realm.json
```

> ⚠️ Replace `https://your-host.com` with your actual Keycloak base URL.

---

## 6. Troubleshooting

| Issue | Possible Cause | Solution |
|-------|----------------|-----------|
| Redirect URI Mismatch | Host or port in JSON does not match your Keycloak server | Replace all `localhost:8080` references with your server hostname |
| Realm not visible | Import failed or realm already exists | Check Keycloak logs for errors and remove any existing realm with the same name |
| Invalid Secret | Client secrets may have expired or changed | Regenerate secrets under `Clients → Credentials` |
| 403 Unauthorized | The client may not have permission or incorrect redirect URI | Verify the client configuration and user roles |

---

## 7. Verifying the Realm Works

Once imported and configured:

1. Go to the login page:
   ```
   http://localhost:8080/realms/igrp/account/
   ```
   (or replace `localhost:8080` with your host)

2. Log in with:
   - **Username:** `superadmin`
   - **Password:** `superadmin`

3. You should access the Keycloak user account console.

---

## 8. Reference

- [Keycloak Documentation – Importing Realms](https://www.keycloak.org/server/importExport)
- [Keycloak 26.x Configuration Guide](https://www.keycloak.org/docs/latest/server_admin/)

---

### Summary

| Step | Action |
|------|--------|
| 1 | Edit JSON if your Keycloak host ≠ `localhost:8080` |
| 2 | Import `igrp-realm.json` via Admin Console or CLI |
| 3 | Verify realm, clients, and users |
| 4 | Test login via Keycloak UI |

---

**Author:** iGRP Team
**Version:** Keycloak 26+
**File:** `igrp-realm.json`