-- V16_1 — Invitee language on t_invitation_entity (i18n, FR-29).
--
-- Stores the language chosen for the invitee (base tag: pt / en / fr). Every
-- invitation email (invite, resend, OTP, cancellation, response) is requested
-- in this language, and it is copied into the new user's metadata.locale when
-- the invitation is accepted.
--
-- Nullable on purpose: invitations created before this feature keep a NULL
-- locale and continue to use igrp.notification.service.invitation.default-locale
-- (FR-33). No backfill.
--
-- Guarded and idempotent, mirroring V10_1 / V11_1: Flyway runs before Hibernate,
-- so on a fresh database the table does not exist yet (ddl-auto=update then
-- creates it, including this column, from InvitationEntity). On any previously
-- booted database the column is added here.

DO $$
BEGIN
    IF to_regclass('t_invitation_entity') IS NULL THEN
        RAISE NOTICE 't_invitation_entity not present yet; skipping V16_1 (column created from the entity by Hibernate on a fresh DB).';
        RETURN;
    END IF;

    ALTER TABLE t_invitation_entity ADD COLUMN IF NOT EXISTS locale VARCHAR(8);
END $$;
