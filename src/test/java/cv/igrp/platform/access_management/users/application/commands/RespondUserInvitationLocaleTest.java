package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BE-13 (FR-31): on acceptance the invitation language becomes the user's
 * {@code metadata.locale}. Exercises the helper used by
 * {@link RespondUserInvitationCommandHandler} for the accept path.
 */
class RespondUserInvitationLocaleTest {

    @Test
    void newUserGetsTheInvitationLocale() {
        IGRPUserEntity user = new IGRPUserEntity();

        RespondUserInvitationCommandHandler.applyInvitationLocale(user, "fr", true);

        assertThat(user.getMetadata()).containsEntry("locale", "fr");
    }

    @Test
    void temporaryUserTakesTheInvitationLocaleOverTheIdpSeededOneAndKeepsOtherKeys() {
        IGRPUserEntity user = new IGRPUserEntity();
        user.setMetadata(new LinkedHashMap<>(Map.of("locale", "en-US", "given_name", "John")));

        RespondUserInvitationCommandHandler.applyInvitationLocale(user, "pt-CV", true);

        assertThat(user.getMetadata()).containsEntry("locale", "pt").containsEntry("given_name", "John");
    }

    @Test
    void activatedUserKeepsAnExistingPreference() {
        IGRPUserEntity user = new IGRPUserEntity();
        user.setMetadata(new LinkedHashMap<>(Map.of("locale", "en")));

        RespondUserInvitationCommandHandler.applyInvitationLocale(user, "fr", false);

        assertThat(user.getMetadata()).containsEntry("locale", "en");
    }

    @Test
    void activatedUserWithoutAValidPreferenceTakesTheInvitationLocale() {
        IGRPUserEntity user = new IGRPUserEntity();
        user.setMetadata(new LinkedHashMap<>(Map.of("locale", "de")));

        RespondUserInvitationCommandHandler.applyInvitationLocale(user, "fr", false);

        assertThat(user.getMetadata()).containsEntry("locale", "fr");
    }

    @Test
    void preI18nInvitationLeavesMetadataUntouched() {
        IGRPUserEntity user = new IGRPUserEntity();
        Map<String, Object> metadata = new LinkedHashMap<>(Map.of("given_name", "John"));
        user.setMetadata(metadata);

        RespondUserInvitationCommandHandler.applyInvitationLocale(user, null, true);

        assertThat(user.getMetadata()).isSameAs(metadata).doesNotContainKey("locale");
    }
}
