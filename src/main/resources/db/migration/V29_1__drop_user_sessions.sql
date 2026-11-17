-- V29_1 — iGRP's own sessions are removed (v3-25, #33; spec "Tokens").
--
-- There is no session row and no sid: a user is signed in while they hold an
-- active authorization in AM's store (oauth2_authorization). The browser's SSO
-- session (SPRING_SESSION) is a different mechanism and stays.
--
-- t_user_session_aud is the table Hibernate Envers kept for it.
-- t_refresh_token_tombstone keeps its session_id column, now always null.
DROP TABLE IF EXISTS t_user_session_aud;
DROP TABLE IF EXISTS t_user_session;
DROP SEQUENCE IF EXISTS t_user_session_id_seq;
