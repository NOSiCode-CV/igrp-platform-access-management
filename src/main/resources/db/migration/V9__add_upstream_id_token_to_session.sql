-- V9 — Persist the upstream IdP's original id_token alongside each session.
--
-- WSO2 Identity Server (and other strict IdP implementations) only honor the
-- post_logout_redirect_uri query parameter when the logout request also
-- carries id_token_hint. Without it, WSO2 falls back to its built-in
-- "You successfully logged out" page and the user never lands back on the
-- caller-provided redirect target.
--
-- The id_token in question is the one the upstream IdP issued at login
-- time — not the iGRP-issued id_token we hold in the OidcLogoutAuthentication
-- token at /connect/logout time. To replay it on the upstream logout cascade
-- we have to stash it at issuance time on the session row.
--
-- TEXT for the column type because OIDC id_tokens commonly run 1-4 kB and
-- can exceed VARCHAR limits on some Postgres setups.

ALTER TABLE t_user_session
    ADD COLUMN IF NOT EXISTS upstream_id_token TEXT;
