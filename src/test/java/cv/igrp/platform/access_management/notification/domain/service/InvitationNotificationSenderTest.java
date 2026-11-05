package cv.igrp.platform.access_management.notification.domain.service;

import cv.igrp.framework.notifications.core.adapter.NotificationAdapter;
import cv.igrp.framework.notifications.core.model.Notification;
import cv.igrp.framework.notifications.core.model.NotificationResult;
import cv.igrp.platform.access_management.shared.config.NotificationServiceProperties;
import cv.igrp.platform.notification.client.ApiException;
import cv.igrp.platform.notification.client.api.NotificationsApi;
import cv.igrp.platform.notification.client.errors.ErrorResponse;
import cv.igrp.platform.notification.client.model.NotificationResponse;
import cv.igrp.platform.notification.client.model.SendNotificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BE-12 (FR-32): one retry with the module default ({@code en}) when — and only
 * when — the Notification Service reports that no template exists for the
 * requested locale (OQ-5: 422 + TEMPLATE_ERROR + "No PUBLISHED template").
 * BE-14 (FR-33): a null locale uses {@code invitation.default-locale}.
 */
class InvitationNotificationSenderTest {

    private NotificationsApi api;
    private NotificationAdapter<NotificationResult> legacy;
    private NotificationServiceProperties properties;
    private InvitationNotificationSender sender;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        api = mock(NotificationsApi.class);
        legacy = mock(NotificationAdapter.class);
        NotificationResult ok = new NotificationResult();
        ok.setSuccess(true);
        when(legacy.send(any(Notification.class))).thenReturn(ok);

        properties = new NotificationServiceProperties();
        properties.setEnabled(true);
        properties.getInvitation().setDefaultLocale("pt");

        sender = new InvitationNotificationSender(Optional.of(api), Optional.of(properties), legacy,
                "", "", "", "Dear {{user}}, your OTP code is {{otp}}.", "en");
    }

    private static ApiException templateMissing(String locale) {
        return new ApiException(422, new ErrorResponse("TEMPLATE_ERROR",
                "No PUBLISHED template 'user-invitation' for channel EMAIL locale=" + locale));
    }

    private static ApiException missingVariables() {
        return new ApiException(422, new ErrorResponse("TEMPLATE_ERROR",
                "Missing required template variables: [user]"));
    }

    private List<String> sentLocales(int expectedCalls) throws ApiException {
        ArgumentCaptor<SendNotificationRequest> captor = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(api, times(expectedCalls)).send(captor.capture());
        return captor.getAllValues().stream().map(r -> r.getTemplate().getLocale()).toList();
    }

    @Test
    void templateMissingRetriesExactlyOnceWithModuleDefault() throws Exception {
        when(api.send(any())).thenThrow(templateMissing("fr")).thenReturn(new NotificationResponse());

        sender.send("a@b.cv", null, "http://link", "tok", "fr");

        assertThat(sentLocales(2)).containsExactly("fr", "en");
        verify(legacy, never()).send(any());
    }

    @Test
    void retryKeepsTheSameIdempotencyKeyAndTemplate() throws Exception {
        when(api.send(any())).thenThrow(templateMissing("fr")).thenReturn(new NotificationResponse());

        sender.send("a@b.cv", null, "http://link", "tok", "fr");

        ArgumentCaptor<SendNotificationRequest> captor = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(api, times(2)).send(captor.capture());
        assertThat(captor.getAllValues()).extracting(SendNotificationRequest::getIdempotencyKey)
                .containsOnly("user-invitation:tok");
        assertThat(captor.getAllValues()).extracting(r -> r.getTemplate().getCode()).containsOnly("user-invitation");
    }

    @Test
    void failedRetryFallsBackToLegacyMailWithoutAThirdAttempt() throws Exception {
        when(api.send(any())).thenThrow(templateMissing("fr"), templateMissing("en"));

        sender.send("a@b.cv", null, "http://link", "tok", "fr");

        assertThat(sentLocales(2)).containsExactly("fr", "en");
        verify(legacy).send(any());
    }

    @Test
    void missingTemplateVariablesDoNotRetry() throws Exception {
        when(api.send(any())).thenThrow(missingVariables());

        sender.send("a@b.cv", null, "http://link", "tok", "fr");

        assertThat(sentLocales(1)).containsExactly("fr");
        verify(legacy).send(any());
    }

    @Test
    void otherHttpErrorsDoNotRetry() throws Exception {
        when(api.send(any())).thenThrow(new ApiException(500, new ErrorResponse("INTERNAL_ERROR", "boom")));

        sender.send("a@b.cv", null, "http://link", "tok", "fr");

        assertThat(sentLocales(1)).containsExactly("fr");
        verify(legacy).send(any());
    }

    @Test
    void runtimeFailuresDoNotRetry() throws Exception {
        when(api.send(any())).thenThrow(new IllegalStateException("connection refused"));

        sender.send("a@b.cv", null, "http://link", "tok", "fr");

        assertThat(sentLocales(1)).containsExactly("fr");
        verify(legacy).send(any());
    }

    @Test
    void noRetryWhenTheRequestedLocaleAlreadyIsTheModuleDefault() throws Exception {
        when(api.send(any())).thenThrow(templateMissing("en"));

        sender.send("a@b.cv", null, "http://link", "tok", "en");

        assertThat(sentLocales(1)).containsExactly("en");
        verify(legacy).send(any());
    }

    @Test
    void rawMessageWithoutParsedBodyIsStillDetected() {
        assertThat(InvitationNotificationSender.isTemplateMissing(new ApiException(422,
                "{\"code\":\"TEMPLATE_ERROR\",\"message\":\"No PUBLISHED template 'x' for channel EMAIL locale=fr\"}")))
                .isTrue();
        assertThat(InvitationNotificationSender.isTemplateMissing(new ApiException(400, templateMissing("fr").getErrorResponse())))
                .isFalse();
        assertThat(InvitationNotificationSender.isTemplateMissing(new ApiException(422, new ErrorResponse("VALIDATION_ERROR",
                "No PUBLISHED template")))).isFalse();
    }

    @Test
    void sendOtpOrThrowHonoursTheRetry() throws Exception {
        when(api.send(any())).thenThrow(templateMissing("fr")).thenReturn(new NotificationResponse());

        boolean delivered = sender.sendOtpOrThrow("a@b.cv", null, "123456", "tok", "fr");

        assertThat(delivered).isTrue();
        assertThat(sentLocales(2)).containsExactly("fr", "en");
        verify(legacy, never()).send(any());
    }

    @Test
    void everyInvitationEventHonoursTheRetry() throws Exception {
        when(api.send(any())).thenThrow(templateMissing("fr")).thenReturn(new NotificationResponse())
                .thenThrow(templateMissing("fr")).thenReturn(new NotificationResponse())
                .thenThrow(templateMissing("fr")).thenReturn(new NotificationResponse())
                .thenThrow(templateMissing("fr")).thenReturn(new NotificationResponse());

        sender.sendResend("a@b.cv", null, "http://link", "tok", "fr");
        sender.sendCancellation("a@b.cv", null, "tok", "fr");
        sender.sendResponse("a@b.cv", null, "u1", "tok", "fr");
        sender.sendOtp("a@b.cv", null, "123456", "tok", "fr");

        assertThat(sentLocales(8)).containsExactly("fr", "en", "fr", "en", "fr", "en", "fr", "en");
        verify(legacy, never()).send(any());
    }

    // ─── BE-14 ───────────────────────────────────────────────────────────────

    @Test
    void nullLocaleUsesTheConfiguredInvitationDefault() throws Exception {
        when(api.send(any())).thenReturn(new NotificationResponse());

        sender.send("a@b.cv", null, "http://link", "tok", null);

        assertThat(sentLocales(1)).containsExactly("pt");
    }

    @Test
    void nullLocaleFallsBackToModuleDefaultWhenTheConfiguredDefaultHasNoTemplate() throws Exception {
        when(api.send(any())).thenThrow(templateMissing("pt")).thenReturn(new NotificationResponse());

        sender.send("a@b.cv", null, "http://link", "tok", null);

        assertThat(sentLocales(2)).containsExactly("pt", "en");
    }

    @Test
    void disabledServiceGoesStraightToLegacy() throws Exception {
        properties.setEnabled(false);

        sender.send("a@b.cv", null, "http://link", "tok", "fr");

        verify(api, never()).send(any());
        verify(legacy).send(any());
    }
}
