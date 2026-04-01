---
name: Identity-and-Invitation
description: Guidelines for multi-identifier authentication anchored on NIC. Enforces STRICT invite-only access and maps identifiers (Email, Phone, NIC) exclusively from JWT claims without external IAM adapter dependencies (No-Adapter).
---
# SKILL — Identity & Invitation (STRICT + NIC Anchor + Invite-only)

**Projeto/Repo:** `igrp_platform_access_management`  
**Objetivo:** Remover obrigatoriedade de email e suportar identificação/login por **Email**, **Telefone** ou **NIC**, mantendo tudo ligado ao mesmo utilizador (NIC).  
**Política:** `STRICT` + `Invite-only (Opção A)` + `No-Adapter`.

---

## Skill 0 — Baseline (imutável)

- **NIC é o anchor**: `sub` do AUTENTIKA é sempre o **NIC** e é a chave canónica do utilizador.  
- **Email/Telefone são opcionais**: podem existir, mas não são obrigatórios.
- **Invite-only**: se o utilizador autenticado não existir no DB, bloquear acesso e exigir aceitação de convite.
- **STRICT**: aceitar convite **apenas** com o identificador primário do método usado na aceitação.
- **No-Adapter**: sem chamadas admin ao IAM; apenas JWT verify + claims extraction.

---

## Skill 1 — Modelo de dados (NIC-anchored)

### `users`
- `id` (UUID)
- `nic` (string) **UNIQUE NOT NULL**  ← vem de `sub`
- `display_name` (opcional)
- timestamps

### `user_identifiers` (secundários)
- `id`
- `user_id` (FK)
- `type` ENUM(`EMAIL`, `PHONE`)
- `value_normalized`
- `verified` boolean
- UNIQUE(`type`, `value_normalized`)

**Normalização**
- EMAIL: lower+trim
- PHONE: E.164 (quando possível) / trim

---

## Skill 2 — Mapping AUTH_METHOD → Identificador primário

Implementar função **central**:

- `CMD` → `PHONE` usando claim `phone_number`
- `CNI` → `NIC` usando `sub`
- `CREDENTIALS` (credenciais próprias) → `EMAIL` usando claim `email`

**Erros (STRICT)**
- `CMD` sem `phone_number` → `PRIMARY_IDENTIFIER_MISSING`
- credenciais próprias sem `email` → `PRIMARY_IDENTIFIER_MISSING`

---

## Skill 3 — Gatekeeper (Invite-only, Opção A)

Em todos os endpoints de negócio:

1) Validar JWT (issuer/signature/exp)
2) Extrair `nic = sub`
3) Verificar se existe `users.nic == nic`

Se **não existir** e endpoint **não** for aceitação de convite:

```json
{
  "error": "INVITE_REQUIRED",
  "message": "Utilizador não registado. É necessário aceitar um convite para ativar o acesso."
}
```

---

## Skill 4 — Convites (Identifier-first + STRICT-by-design)

### 4.1. Criação de convite
DTO sugerido:

```json
{
  "identifierType": "EMAIL|PHONE|NIC",
  "identifierValue": "string",
  "roles": ["..."] ,
  "expiresAt": "ISO-8601"
}
```

**Derivar automaticamente `allowedAuthMethods`**:
- convite `EMAIL` → `[CREDENTIALS]`
- convite `PHONE` → `[CMD]`
- convite `NIC` → `[CNI]`

---

## Skill 5 — Aceitar convite (STRICT + NIC anchor)

### Entrada
- `invitationId`
- JWT claims

### Passos
1) Validar JWT
2) `nic = sub`
3) `method = auth_method`
4) `primary = getPrimaryIdentifier(method, claims)`
5) Carregar convite (PENDING)
6) Validar STRICT:
   - `method in inv.allowedAuthMethods`
   - `inv.identifierType == primary.type`
   - `normalize(inv.identifierValue) == normalize(primary.value)`
7) Resolver utilizador:
   - `user = findByNic(nic)`
   - se `null` → `createUser(nic, displayName=claims.name)`
8) Persistir identificadores secundários (best-effort):
   - se `email` existe → upsert EMAIL (verified)
   - se `phone_number` existe → upsert PHONE (verified)
9) Atribuir roles no DB (DB-only)
10) Marcar convite como `ACCEPTED`

---

## Skill 6 — Colisão e segurança

- UNIQUE global para `EMAIL` e `PHONE`:
  - se email/phone já pertencer a outro `user_id` → erro `IDENTIFIER_COLLISION`
- Registar auditoria:
  - `INVITE_REQUIRED_BLOCK`
  - `INVITE_ACCEPTED`
  - `INVITE_ACCEPT_FAILED_STRICT_MISMATCH`
  - `IDENTIFIER_COLLISION`

---

## Skill 7 — No-Adapter compliance (o que NÃO fazer)

- Não chamar `resolveUser`/admin APIs do IAM.
- Não sincronizar roles/departments/permissions com provider.
- Não criar utilizadores fora do fluxo de convite.

---

## Critérios de aceitação (Done)

- Email não é obrigatório em nenhum fluxo.
- Acesso de utilizador não existente no DB é bloqueado (invite-only).
- Aceitação de convite obedece a STRICT e é determinística.
- `sub` (NIC) liga sempre o mesmo utilizador.
- Nenhuma dependência de operações admin no IAM.
