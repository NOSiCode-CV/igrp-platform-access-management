DO $$
BEGIN
    IF to_regclass('public.t_oauth_client') IS NOT NULL THEN
        CREATE TABLE IF NOT EXISTS t_oauth_client_post_logout_redirect_uri (
            client_id  uuid         NOT NULL,
            post_logout_redirect_uri varchar(2048) NOT NULL,
            CONSTRAINT fk_post_logout_client
                FOREIGN KEY (client_id) REFERENCES t_oauth_client (id) ON DELETE CASCADE
        );

        CREATE INDEX IF NOT EXISTS idx_post_logout_redirect_uri_client_id
            ON t_oauth_client_post_logout_redirect_uri (client_id);
    END IF;
END $$;
