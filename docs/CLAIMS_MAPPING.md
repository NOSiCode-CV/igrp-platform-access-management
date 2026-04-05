---
name: claims-mapping
description: Guidelines for standardizing the extraction, validation, and mapping of OIDC claims into a unified internal User Profile, supporting various authentication methods (CNI, CMD) with a focus on security and privacy.
---

# Skill — Claims Parsing and Mapping (CNI, CMD, etc.)

> **Purpose**: Standardize **how to extract, validate, and map** OIDC claims (ID Token and/or `/userinfo`) into a consistent internal **User Profile**, covering different authentication methods (**CNI**, **CMD**, custom credentials), ensuring **security**, **privacy**, and **interoperability** across services.

---

## 1) Principles

1. **JWT-first**: prioritize claims from the **ID Token**; use `/userinfo` only to complement when necessary.
2. **Canonical Identity**: **`sub`** (Subject) is the unique external identifier.
3. **Normalization**: convert and standardize formats (**e-mail**, **phone E.164**, **dates ISO-8601**, **NIC** without separators).
4. **Defensive Approach**: validate **issuer/signature/expiration** before consuming claims; gracefully handle missing claims or data variations.
5. **Privacy**: persist the **strict minimum necessary** (PII); strictly avoid logging sensitive data.

---

## 2) Internal User Model (Target)

> Adjust according to your API domain; keep field names/formats stable.

```yaml
UserProfile:
  external_id: string          # OIDC 'sub'
  issuer: string               # OIDC 'iss'
  name:
    full: string               # 'name' or concatenation of parts
    given: string              # 'given_name'
    family: string             # 'family_name'
  email:
    address: string            # normalized, lowercase
    verified: boolean          # 'email_verified' if it exists
  phone:
    number: string             # E.164
    verified: boolean?         # inferred from AMR (e.g.: OTP SMS)
  national_id:
    nic: string                # NIC normalized (e.g.: remove spaces/hyphens)
    source: string             # 'CNI' | 'CMD' | 'CREDENTIALS'
  auth:
    method: string             # 'CNI' | 'CMD' | 'PASSWORD' | 'MIXED'
    amr: string[]              # Authentication Methods Reference
    acr: string                # Authentication Context Class Reference
  birthdate: string?           # ISO YYYY-MM-DD
  locale: string?              # 'pt-CV', 'pt-PT', 'en-US', etc.
  updated_at: string           # ISO timestamp of when claims were consolidated
```

---

## 3) Mapping Claims → Model (Specification)

| Target Field                    | Probable Claim(s)                              | Parsing/Normalization Rules |
|---                              |---                                             |---|
| `external_id`                   | `sub`                                          | Mandatory. Opaque string. |
| `issuer`                        | `iss`                                          | Store for auditing/validation. |
| `name.full`                     | `name` \| `given_name + family_name`            | Prefer `name`; otherwise, concatenate and normalize spaces. |
| `name.given`/`name.family`      | `given_name` / `family_name`                   | Extract if present. |
| `email.address`                 | `email` \| `preferred_username`                 | Lowercase/trim; validate format. |
| `email.verified`                | `email_verified`                               | Boolean; default to `false` if missing. |
| `phone.number`                  | `phone_number`                                 | Normalize to **E.164**; remove noise. |
| `national_id.nic`               | `NIC` \| `nic` \| `national_id` (custom)       | Remove spaces/hyphens; uppercase if applicable. |
| `national_id.source`            | `auth_method`                                  | Map → `CNI`/`CMD`/`CREDENTIALS`. |
| `auth.method`                   | `auth_method` \| `amr`                         | If `amr` contains smartcard → `CNI`; if OTP/SMS → `CMD`. |
| `auth.amr`                      | `amr` (array)                                  | Preserve array. |
| `auth.acr`                      | `acr`                                          | Preserve if it exists. |
| `birthdate`                     | `dob` \| `birthdate`                           | Convert to `YYYY-MM-DD`. |
| `locale`                        | `locale`                                       | Validate against BCP 47 pattern if needed. |
| `updated_at`                    | (generated)                                    | `Instant.now()` when consolidating claims. |

> **Specific Rules**: 
> - **CNI**: `NIC` is mandatory; `auth.method = 'CNI'`. 
> - **CMD**: `phone_number` is recommended; `auth.method = 'CMD'`; `phone.verified = true` if AMR contains `otp/sms`.
> - **PASSWORD**: `auth.method = 'PASSWORD'`; require valid `email` **or** `phone_number`.

---

## 4) Normalization (Suggested Mechanisms)

- **E‑mail** → lowercase, trim; simple regex format validation. 
- **Phone** → E.164 (remove spaces, `()` and `-`; prepend country code if local policy allows — e.g., `+238`).
- **NIC** → remove spaces/hyphens; uppercase; validate pattern/length against country norms. 
- **Date** → accept `YYYY-MM-DD` or `DD/MM/YYYY` strings and always convert to ISO `YYYY-MM-DD`.

---

## 5) Implementation (Spring Security / OIDC)

> [!WARNING]
> **Warning about Scopes and Token Types:** In order for specific identity claims like `NIC`, `phone_number`, or authentication methods info (`amr`) to be delivered by WSO2, the application must correctly request their `scopes` (e.g.: `openid profile cni cmd`).
> 
> **Strict Extraction Policy:** The `IgrpJwtAuthenticationConverter` operates exclusively on the Token passed to the Resource Server (API) via the `Authorization: Bearer` header. The system is designed to **extract all user information strictly from the provided Token** (preferentially the enriched **ID Token**), and **will NOT invoke the `/oauth2/userinfo` endpoint**.
> 
> **Architecture Requirement:**
> - If crucial identities (`NIC`, `phone_number`) or context (`amr`) are missing from a standard Access Token, the client/UI layer **must** provide the `ID Token` directly to the API, OR the WSO2 Identity Provider must be configured to mirror all necessary OIDC claims directly into the Access Token.

In Spring Security, avoid extracting and mapping claims inside the presentation layer (Controllers). The ideal approach is to extend the **`OidcUserService`** (for OIDC Login flows) and immediately populate a typed object (Record/POJO), integrating it seamlessly into the `SecurityContext`.

### 5.1. Canonical Profile Class (Record)

Create a Java Record (or Class) to ensure Type Safety and autocomplete features across the entire project.

```java
import java.util.List;

public record UserProfile(
    String externalId,
    String issuer,
    String fullName,
    String email,
    String phone,
    String nic,
    String authMethod,
    List<String> amr
) {}
```

### 5.2. Mapping in the Security Layer (OidcUserService)

```java
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CustomOidcUserService extends OidcUserService {

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        // 1. Load the standard OIDC user via base class
        OidcUser oidcUser = super.loadUser(userRequest);
        Map<String, Object> c = oidcUser.getClaims();

        // 2. Defensive Extraction using Helpers
        String sub = req(c, "sub");
        String iss = opt(c, "iss");
        String name = coalesce(opt(c, "name"), join(opt(c, "given_name"), opt(c, "family_name")));
        String email = normalizeEmail(coalesce(opt(c, "email"), opt(c, "preferred_username")));
        String phone = normalizePhone(opt(c, "phone_number"));
        String nic = normalizeNic(coalesce(opt(c, "NIC"), opt(c, "nic"), opt(c, "national_id")));
        
        var amr = optArray(c, "amr");
        String method = detectAuthMethod(opt(c, "auth_method"), amr);

        // 3. Critical Business Validation Rules
        if ("CNI".equals(method) && nic == null) {
            throw new OAuth2AuthenticationException(
                new OAuth2Error("missing_nic"), "Login via CNI requires the Civil Identification Number (NIC)."
            );
        }

        // 4. Build the Profile Object (Type Safety)
        UserProfile profile = new UserProfile(
            sub, iss, nullSafe(name), nullSafe(email), nullSafe(phone), nullSafe(nic), method, amr
        );

        // 5. Return the Custom Principal Object
        // Replace with your custom OidcUser implementation, e.g.: new iGrpUserDetails(oidcUser, profile);
        return oidcUser; 
    }

    // —— Helpers (implement according to your conventions) ——
    private static String req(Map<String,Object> c, String k) {
        var v = c.get(k);
        if (v == null || String.valueOf(v).isBlank()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("missing_claim"), "Mandatory claim is missing: " + k);
        }
        return String.valueOf(v).trim();
    }
  
    private static String opt(Map<String,Object> c, String k) {
        var v = c.get(k); return v == null ? null : String.valueOf(v).trim();
    }
    private static java.util.List<String> optArray(Map<String,Object> c, String k) {
        var v = c.get(k);
        if (v instanceof java.util.Collection<?> col) return col.stream().map(String::valueOf).toList();
        return java.util.List.of();
    }
    private static boolean optBool(Map<String,Object> c, String k, boolean d) {
        var v = c.get(k); return v == null ? d : Boolean.parseBoolean(String.valueOf(v));
    }
    private static String coalesce(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v; return null;
    }
    private static String join(String a, String b) {
        if ((a == null || a.isBlank()) && (b == null || b.isBlank())) return null;
        return (nullSafe(a) + " " + nullSafe(b)).trim().replaceAll("\\s+", " ");
    }
    private static String normalizeEmail(String email) {
        if (email == null) return null; var e = email.trim().toLowerCase();
        return e.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") ? e : null;
    }
    private static String normalizePhone(String phone) {
        if (phone == null) return null; var digits = phone.replaceAll("[^\\d+]", "");
        // Local rules may prepend country codes if applicable
        return digits.startsWith("+") ? digits : digits;
    }
    private static String normalizeNic(String nic) {
        if (nic == null) return null; return nic.replaceAll("[\\s-]", "").toUpperCase();
    }
    private static String normalizeDate(String d) {
        if (d == null) return null;
        if (d.matches("^\\d{4}-\\d{2}-\\d{2}$")) return d;
        if (d.matches("^\\d{2}/\\d{2}/\\d{4}$")) { var p = d.split("/"); return p[2]+"-"+p[1]+"-"+p[0]; }
        return null;
    }
    private static String detectAuthMethod(String method, java.util.List<String> amr) {
        if (method != null && !method.isBlank()) return method.toUpperCase();
        var amrLower = amr.stream().map(String::toLowerCase).toList();
        boolean cni = amrLower.stream().anyMatch(x -> x.contains("sc") || x.contains("smartcard") || x.contains("hwk"));
        boolean cmd = amrLower.stream().anyMatch(x -> x.contains("otp") || x.contains("sms"));
        if (cni && cmd) return "MIXED";
        if (cni) return "CNI";
        if (cmd) return "CMD";
        return "PASSWORD";
    }
    private static String sourceFor(String method) {
        return switch (method) {
            case "CNI" -> "CNI";
            case "CMD" -> "CMD";
            default -> "CREDENTIALS";
        };
    }
    private static String nullSafe(String s) { return s == null ? "" : s; }
}
```

> **Notes**
> - Avoid logging the `NIC`, `phone_number`, or email at **INFO/ERROR levels**.
> - If your project acts as a **Resource Server** (API-only layer that just receives and validates the Bearer Token), do not use `OidcUserService` but rather a custom `JwtAuthenticationConverter` applying this exact same extraction logic.

---

## 6) Tests (Examples)

### 6.1. CNI (Smartcard)
```java
// Arrange: claims containing NIC and AMR indicating 'smartcard'
// Assert: auth.method == "CNI"; national_id.nic is normalized; email can be optional
```

### 6.2. CMD (OTP/SMS)
```java
// Arrange: claims containing phone_number in E.164 and AMR indicating 'otp'/'sms'
// Assert: auth.method == "CMD"; phone.verified == true
```

### 6.3. Password (Credentials)
```java
// Arrange: minimal claims with sub/iss and preferred_username mapped to email
// Assert: auth.method == "PASSWORD"; email.address is properly normalized
```

---

## 7) Rejection Rules

- Missing `sub` → **reject**.
- Unknown `iss` → **reject**.
- `auth.method = CNI` without a `NIC` → **incomplete** (deny access or prompt for more info according to policy).

---

## 8) Privacy and Retention Restrictions

- Persist only the normalized profile (avoid storing `raw_claims` in the database).
- Mask PII in logs (e.g., `******2210`, `*********003H`).
- Establish strict retention/purging rules for sensitive fields (GDPR/Data Privacy laws compliance).

---

## 9) Extensions

- **Addresses/Locale**: Map standard OpenID `address` or `locale` (BCP 47).
- **MFA (Multi-Factor Authentication)**: Enrich the `auth` object with metadata (e.g., OTP channel, second factor timestamp).
- **Issuer-specific behavior**: Allow rules overrides per issuer (different behaviors keyed by `iss`).

---

## 10) Adoption Checklist

- [ ] Implement robust parsing/normalization utility functions.
- [ ] Cover CNI/CMD/PASSWORD endpoints with contract tests.
- [ ] Ensure token validation (issuer/signature/expiration) happens before mapping logic.
- [ ] Document specific normalization rules (emails, phones, NIC, dates).
- [ ] Prevent PII data from leaking into standard application logs; enable masking.
- [ ] Broadcast the **Schema** of the `UserProfile` to allow safe consumption by other teams.

---

> **Tip**: If the identity provider (e.g., AUTENTIKA/WSO2) issues custom claims (e.g.: `NIC`, `auth_method`), maintain a versioned **mapping dictionary** to prevent breaking downstream consumers when new fields are introduced.

---

## ✅ Implementation Status (Current Codebase)
- **OIDC Native Context**: The integration relies heavily on native Spring Security structures. We map the ID Token directly into an `IgrpOidcUser` available natively at `SecurityContextHolder`.
- **Roles Extraction**: `IgrpJwtAuthenticationConverter` now effectively parses the `roles` and `groups` arrays from incoming tokens and maps them to authorities.
- **Sub-claim mapping**: The `IgrpJwtAuthenticationConverter` relies heavily on validating the `sub` claim for user uniqueness.
- **CMD/CNI & Extended Parsing**: `IgrpJwtAuthenticationConverter` and `UserProfile` reliably handle extraction of `NIC`, E.164 `phone_number` and resolving `auth_method` (including fallbacks to `CREDENTIALS`), actively fulfilling the NO_ADAPTER schema rules. (COMPLETED).
