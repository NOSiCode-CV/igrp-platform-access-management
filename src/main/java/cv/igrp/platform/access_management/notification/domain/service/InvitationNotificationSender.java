package cv.igrp.platform.access_management.notification.domain.service;

import cv.igrp.framework.notifications.core.adapter.NotificationAdapter;
import cv.igrp.framework.notifications.core.model.Notification;
import cv.igrp.framework.notifications.core.model.NotificationResult;
import cv.igrp.platform.access_management.shared.config.NotificationServiceProperties;
import cv.igrp.platform.notification.client.ApiException;
import cv.igrp.platform.notification.client.api.NotificationsApi;
import cv.igrp.platform.notification.client.constants.Channel;
import cv.igrp.platform.notification.client.constants.ChannelStrategy;
import cv.igrp.platform.notification.client.model.NotificationResponse;
import cv.igrp.platform.notification.client.model.RecipientDto;
import cv.igrp.platform.notification.client.model.SendNotificationRequest;
import cv.igrp.platform.notification.client.model.TemplateRefDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sends the user-invitation email. Primary path: iGRP Notification Service
 * via its Java client, using the framework-seeded {@code user-invitation}
 * template. Fallback path: the legacy {@link NotificationAdapter} — which is
 * wired to Spring Mail in production and to a no-op elsewhere — with the same
 * plaintext-ish body previous versions already used.
 *
 * <p>The fallback triggers on any failure of the primary path:
 * <ul>
 *   <li>{@link ApiException} for both service errors (non-2xx) and transport
 *       failures (connection refused / timeout — the client wraps IO exceptions
 *       as {@code ApiException}).</li>
 *   <li>Any other {@link RuntimeException}, e.g. token acquisition problems
 *       from the underlying {@code ClientCredentialsTokenProvider}.</li>
 * </ul>
 * so the invitation flow never fails for the caller as long as the fallback is
 * reachable. The distinction is preserved in the WARN log line so operators
 * can distinguish "service down" from "service errored" without changing code.
 *
 * <p>The primary path can be disabled entirely with
 * {@code igrp.notification.service.enabled=false}. In that case
 * {@link NotificationServiceConfig} skips creating the {@link NotificationsApi}
 * bean and this service sees an empty {@link Optional} — it goes straight to
 * the fallback without any WARN noise.
 */
@Service
public class InvitationNotificationSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(InvitationNotificationSender.class);

    private static final String SUBJECT_FALLBACK = "iGRP User Invitation";

    private final Optional<NotificationsApi> notificationsApi;
    private final NotificationServiceProperties properties;
    private final NotificationAdapter<NotificationResult> legacyAdapter;
    private final String legacyBodyTemplate;

    public InvitationNotificationSender(
            Optional<NotificationsApi> notificationsApi,
            NotificationServiceProperties properties,
            NotificationAdapter<NotificationResult> legacyAdapter,
            @org.springframework.beans.factory.annotation.Value("${igrp.mail.invite.template:}") String legacyBodyTemplate) {
        this.notificationsApi = notificationsApi;
        this.properties = properties;
        this.legacyAdapter = legacyAdapter;
        this.legacyBodyTemplate = legacyBodyTemplate;
    }

    /**
     * Send an invitation email. Never throws — logs and falls back on failure
     * so the caller (invitation command handler) can persist the invitation
     * even when both mail paths are temporarily broken (the token is still
     * usable via link even if the email never arrives).
     *
     * @param email               recipient email; also used as display name when {@code userDisplayName} is blank
     * @param userDisplayName     Handlebars {@code {{user}}}; falls back to {@code email} when blank
     * @param invitationLink      Handlebars {@code {{link}}}; the accept URL sent to the invitee
     * @param invitationToken     used as {@code businessKey} and to form the idempotency key so retries do not duplicate the email
     * @param locale              template locale (e.g. {@code "en"} or {@code "pt"}); when blank uses the configured default
     */
    public void send(String email,
                     String userDisplayName,
                     String invitationLink,
                     String invitationToken,
                     String locale) {

        String resolvedDisplayName = (userDisplayName != null && !userDisplayName.isBlank())
                ? userDisplayName : email;

        if (properties.isEnabled() && notificationsApi.isPresent()) {
            try {
                SendNotificationRequest request = buildServiceRequest(
                        email, resolvedDisplayName, invitationLink, invitationToken, locale);
                NotificationResponse response = notificationsApi.get().send(request);
                LOGGER.info("Invitation sent via Notification Service (invitationToken={}, notificationId={})",
                        invitationToken, response != null ? response.getId() : null);
                return;
            } catch (ApiException ex) {
                // Both service errors (non-2xx) and transport failures (the client wraps
                // IOException as ApiException) land here. getCode() is the HTTP status
                // (0 for a transport failure).
                LOGGER.warn("Notification Service send failed (httpCode={}, message={}), falling back to legacy mail sender for invitationToken={}",
                        ex.getCode(), ex.getMessage(), invitationToken);
            } catch (RuntimeException ex) {
                LOGGER.warn("Notification Service unavailable, falling back to legacy mail sender for invitationToken={}",
                        invitationToken, ex);
            }
        }

        sendLegacyFallback(email, resolvedDisplayName, invitationLink, invitationToken);
    }

    private SendNotificationRequest buildServiceRequest(String email,
                                                        String userDisplayName,
                                                        String invitationLink,
                                                        String invitationToken,
                                                        String requestedLocale) {

        NotificationServiceProperties.Invitation invitationProps = properties.getInvitation();

        TemplateRefDto template = new TemplateRefDto();
        template.setCode(invitationProps.getTemplateCode());
        template.setLocale((requestedLocale != null && !requestedLocale.isBlank())
                ? requestedLocale : invitationProps.getDefaultLocale());

        RecipientDto recipient = new RecipientDto();
        recipient.setEmail(email);

        // Only include optional variables when configured so the template does not
        // receive null values it would render as the literal string "null".
        Map<String, Object> variables = new HashMap<>();
        variables.put("project", invitationProps.getProject());
        variables.put("user", userDisplayName);
        variables.put("link", invitationLink);
        variables.put("year", String.valueOf(Year.now().getValue()));
        String appCenterUrl = invitationProps.getAppCenterUrl();
        if (appCenterUrl != null && !appCenterUrl.isBlank()) {
            variables.put("appCenterUrl", appCenterUrl);
        }

        SendNotificationRequest request = new SendNotificationRequest();
        request.setChannelStrategy(ChannelStrategy.SPECIFIC);
        request.setChannels(List.of(Channel.EMAIL));
        request.setTemplate(template);
        request.setRecipients(List.of(recipient));
        request.setVariables(variables);
        // Idempotency: retries — either ours from a background worker or the service's
        // own — will not duplicate the email, since the service dedupes on this key.
        request.setIdempotencyKey("user-invitation:" + invitationToken);
        request.setBusinessKey(invitationToken);
        return request;
    }

    private void sendLegacyFallback(String email,
                                    String userDisplayName,
                                    String invitationLink,
                                    String invitationToken) {
        Notification notification = new Notification();
        notification.setRecipients(List.of(email));
        notification.setSubject(SUBJECT_FALLBACK);
        notification.setContent(renderLegacyBody(userDisplayName, invitationLink));
        notification.setMetadata(Map.of(
                "invitationToken", invitationToken,
                "email", email));
        try {
            legacyAdapter.send(notification);
        } catch (Exception ex) {
            // The legacy adapter already logs failures internally; catch here as well
            // so the surrounding invitation command handler never sees an exception
            // from the mail path (persisting the invitation must succeed either way).
            LOGGER.error("Legacy mail fallback failed for invitationToken={}", invitationToken, ex);
        }
    }

    private String renderLegacyBody(String userDisplayName, String invitationLink) {
        String template = (legacyBodyTemplate == null || legacyBodyTemplate.isBlank())
                ? "Dear {{user}}, you were invited to the iGRP platform.\n\nPlease click on the link below to accept the invitation:\n{{link}}\n\nBest Regards.\niGRP"
                : legacyBodyTemplate;
        return template
                .replace("{{user}}", userDisplayName)
                // Both {{link}} (Notification Service convention) and {{url}} (legacy env
                // var used the old name) map to the same value, so operators can keep
                // their existing IGRP_MAIL_INVITE_TEMPLATE unchanged.
                .replace("{{link}}", invitationLink)
                .replace("{{url}}", invitationLink);
    }
}
