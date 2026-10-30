-- Rollback of the Spring Boot 4 cutover, back to the Boot 3.5 image.
--
-- Run with the API scaled to 0, before starting the old image, and flush the
-- service's Redis database in the same window: Boot 3.5 cannot read the
-- Jackson 3 data the new version wrote.
--
-- Removing the history row matters: the old image does not know migration 17.1,
-- and Flyway's validation would refuse to start.

DELETE FROM oauth2_authorization;
DELETE FROM spring_session;
DELETE FROM flyway_schema_history WHERE version = '17.1';
