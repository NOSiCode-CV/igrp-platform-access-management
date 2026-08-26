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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Central sender for every invitation-related email out of Access Management.
 * Primary path: iGRP Notification Service via its Java client, using the
 * framework-seeded templates. Fallback path: the legacy
 * {@link NotificationAdapter} (wired to Spring Mail in production, no-op
 * elsewhere) with the current legacy body from {@code IGRP_MAIL_INVITE_*}
 * / {@code IGRP_MAIL_OTP_*} — so operators can flip the primary path off
 * with {@code IGRP_NOTIFICATION_SERVICE_ENABLED=false} or lose the primary
 * path silently on any {@link ApiException}/{@link RuntimeException} without
 * ever failing the surrounding command.
 *
 * <p>Method surface, one per invitation event:
 * <ul>
 *   <li>{@link #send} — initial invite (template {@code user-invitation}).</li>
 *   <li>{@link #sendResend} — resend the same invite (same template by default).</li>
 *   <li>{@link #sendCancellation} — notify invitee of admin cancellation.</li>
 *   <li>{@link #sendResponse} — confirm the invitee's acceptance.</li>
 *   <li>{@link #sendOtp} — email the OTP during invitation email validation.</li>
 * </ul>
 * All five funnel into {@link #dispatch(SendSpec)} which owns the try/catch/
 * fall-back mechanics so each event method stays tiny.
 */
@Service
public class InvitationNotificationSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(InvitationNotificationSender.class);

    private static final String SUBJECT_INVITATION = "iGRP User Invitation";
    private static final String SUBJECT_CANCELLATION = "iGRP User Invitation";
    private static final String SUBJECT_RESPONSE = "iGRP Invitation Response";
    private static final String SUBJECT_OTP = "iGRP Security Code";

    private final Optional<NotificationsApi> notificationsApi;
    // Optional because the properties bean itself may be absent — e.g. a
    // consumer of this SDK that never enabled the Notification Service, or a
    // slim test slice that only wires the legacy path. Empty is treated as
    // "primary path disabled" (defaults applied to whatever falls through to
    // configuredProperties()), so the sender always has SOMETHING to work with
    // and never NPEs on missing config.
    private final Optional<NotificationServiceProperties> propertiesOpt;
    private final NotificationAdapter<NotificationResult> legacyAdapter;

    private final String legacyInvitationBody;
    private final String legacyCancellationBody;
    private final String legacyResponseBody;
    private final String legacyOtpBody;

    public InvitationNotificationSender(
            Optional<NotificationsApi> notificationsApi,
            Optional<NotificationServiceProperties> propertiesOpt,
            NotificationAdapter<NotificationResult> legacyAdapter,
            @Value("${igrp.mail.invite.template:}") String legacyInvitationBody,
            @Value("${igrp.mail.invite.cancellation.template:}") String legacyCancellationBody,
            @Value("${igrp.mail.invite.response.template:}") String legacyResponseBody,
            @Value("${igrp.mail.otp.template:Dear user, your OTP code is {{otp}}.}") String legacyOtpBody) {
        this.notificationsApi = notificationsApi;
        this.propertiesOpt = propertiesOpt;
        this.legacyAdapter = legacyAdapter;
        this.legacyInvitationBody = legacyInvitationBody;
        this.legacyCancellationBody = legacyCancellationBody;
        this.legacyResponseBody = legacyResponseBody;
        this.legacyOtpBody = legacyOtpBody;
    }

    /**
     * Effective properties. Returns a fresh default-valued instance when the
     * properties bean is absent, so downstream code always sees non-null
     * template codes / project / locale. The default instance has
     * {@code enabled=false}, so callers checking {@link NotificationServiceProperties#isEnabled()}
     * naturally short-circuit to the fallback path.
     */
    private NotificationServiceProperties properties() {
        return propertiesOpt.orElseGet(NotificationServiceProperties::new);
    }

    // ─── event methods ────────────────────────────────────────────────────

    /** Initial invitation email. */
    public void send(String email,
                     String userDisplayName,
                     String invitationLink,
                     String invitationToken,
                     String locale) {
        String user = fallbackDisplayName(userDisplayName, email);
        Map<String, Object> variables = commonInvitationVariables(user, invitationLink);
        dispatch(new SendSpec(
                properties().getInvitation().getTemplateCode(),
                email,
                locale,
                variables,
                "user-invitation:" + invitationToken,
                invitationToken,
                SUBJECT_INVITATION,
                Map.of("invitationToken", invitationToken, "email", email),
                () -> renderLegacy(legacyInvitationBody,
                        "Dear {{user}}, you were invited to the iGRP platform.\n\nPlease click on the link below to accept the invitation:\n{{link}}\n\nBest Regards.\niGRP",
                        Map.of("user", user, "link", invitationLink, "url", invitationLink))));
    }

    /**
     * Resend of an existing invitation with a fresh token. Same content as
     * {@link #send} by default; kept as a distinct method so that if operations
     * later publishes a dedicated "user-invitation-resend" template, only this
     * method's template code needs re-pointing.
     */
    public void sendResend(String email,
                           String userDisplayName,
                           String invitationLink,
                           String invitationToken,
                           String locale) {
        String user = fallbackDisplayName(userDisplayName, email);
        Map<String, Object> variables = commonInvitationVariables(user, invitationLink);
        dispatch(new SendSpec(
                properties().getInvitation().getResendTemplateCode(),
                email,
                locale,
                variables,
                // Distinct idempotency key: the resend genuinely IS a different email
                // (new token, new link), so it MUST bypass the initial-invite dedup
                // window on the Notification Service side.
                "user-invitation-resend:" + invitationToken,
                invitationToken,
                SUBJECT_INVITATION,
                Map.of("invitationToken", invitationToken, "email", email),
                () -> renderLegacy(legacyInvitationBody,
                        "Dear {{user}}, you were invited to the iGRP platform.\n\nPlease click on the link below to accept the invitation:\n{{link}}\n\nBest Regards.\niGRP",
                        Map.of("user", user, "link", invitationLink, "url", invitationLink))));
    }

    /** Notify the invitee that their pending invitation was cancelled. */
    public void sendCancellation(String email,
                                 String userDisplayName,
                                 String invitationToken,
                                 String locale) {
        String user = fallbackDisplayName(userDisplayName, email);
        Map<String, Object> variables = new HashMap<>();
        variables.put("project", properties().getInvitation().getProject());
        variables.put("user", user);
        variables.put("year", String.valueOf(Year.now().getValue()));
        String appCenterUrl = properties().getInvitation().getAppCenterUrl();
        if (appCenterUrl != null && !appCenterUrl.isBlank()) {
            variables.put("appCenterUrl", appCenterUrl);
        }
        dispatch(new SendSpec(
                properties().getInvitation().getCancellationTemplateCode(),
                email,
                locale,
                variables,
                "user-invitation-cancelled:" + invitationToken,
                invitationToken,
                SUBJECT_CANCELLATION,
                Map.of("invitationToken", invitationToken, "email", email),
                () -> renderLegacy(legacyCancellationBody,
                        "Dear {{user}}, your invitation to the iGRP platform was cancelled.",
                        Map.of("user", user))));
    }

    /**
     * Confirm to the invitee that their acceptance succeeded.
     *
     * @param invitationToken carried through for logging + idempotency; may be null
     *                        when the caller no longer has the original token in hand
     *                        (in that case the userId doubles as the dedup key)
     */
    public void sendResponse(String email,
                             String userDisplayName,
                             String userId,
                             String invitationToken,
                             String locale) {
        String user = fallbackDisplayName(userDisplayName, email);
        Map<String, Object> variables = new HashMap<>();
        variables.put("project", properties().getInvitation().getProject());
        variables.put("user", user);
        variables.put("year", String.valueOf(Year.now().getValue()));
        String appCenterUrl = properties().getInvitation().getAppCenterUrl();
        if (appCenterUrl != null && !appCenterUrl.isBlank()) {
            variables.put("appCenterUrl", appCenterUrl);
        }
        String dedupKey = invitationToken != null && !invitationToken.isBlank() ? invitationToken : userId;
        dispatch(new SendSpec(
                properties().getInvitation().getResponseTemplateCode(),
                email,
                locale,
                variables,
                "user-invitation-responded:" + dedupKey,
                dedupKey,
                SUBJECT_RESPONSE,
                Map.of("userId", userId != null ? userId : "", "email", email),
                () -> renderLegacy(legacyResponseBody,
                        "Dear {{user}}, you accepted the invite to the iGRP platform successfully.",
                        Map.of("user", user))));
    }

    /**
     * Email an OTP as part of invitation email validation.
     *
     * <p>OTP delivery is a critical step in the invitation flow — a failure here
     * (both primary and fallback) leaves the invitee unable to progress. Callers
     * who need to raise the original {@code IGRP_AUTH_INVITATION_OTP_SEND_FAILED}
     * business exception on total failure should call {@link #sendOtpOrThrow}
     * instead; this method swallows any legacy-fallback exception the same way
     * as the other methods.
     */
    public void sendOtp(String email,
                        String otpCode,
                        String invitationToken,
                        String locale) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("project", properties().getInvitation().getProject());
        variables.put("otp", otpCode);
        variables.put("year", String.valueOf(Year.now().getValue()));
        String appCenterUrl = properties().getInvitation().getAppCenterUrl();
        if (appCenterUrl != null && !appCenterUrl.isBlank()) {
            variables.put("appCenterUrl", appCenterUrl);
        }
        dispatch(new SendSpec(
                properties().getInvitation().getOtpTemplateCode(),
                email,
                locale,
                variables,
                // Include the OTP code in the idempotency key so re-issuing a NEW
                // OTP for the same invitation is not dedup'd as a retry of the
                // previous OTP send.
                "user-invitation-otp:" + invitationToken + ":" + otpCode,
                invitationToken,
                SUBJECT_OTP,
                Map.of("invitationToken", invitationToken, "email", email),
                () -> renderLegacy(legacyOtpBody,
                        "Dear user, your OTP code is {{otp}}.",
                        Map.of("otp", otpCode))));
    }

    /**
     * Variant of {@link #sendOtp} that surfaces total failure to the caller so
     * the invitation validation endpoint can respond with the existing
     * {@code IGRP_AUTH_INVITATION_OTP_SEND_FAILED} business error — matches the
     * pre-integration behavior of {@link cv.igrp.platform.access_management.users.application.commands.ValidateInvitationEmailCommandHandler}.
     *
     * @return {@code true} when either the primary or fallback path succeeded;
     *         {@code false} when both failed
     */
    public boolean sendOtpOrThrow(String email,
                                  String otpCode,
                                  String invitationToken,
                                  String locale) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("project", properties().getInvitation().getProject());
        variables.put("otp", otpCode);
        variables.put("year", String.valueOf(Year.now().getValue()));
        String appCenterUrl = properties().getInvitation().getAppCenterUrl();
        if (appCenterUrl != null && !appCenterUrl.isBlank()) {
            variables.put("appCenterUrl", appCenterUrl);
        }
        return dispatchWithResult(new SendSpec(
                properties().getInvitation().getOtpTemplateCode(),
                email,
                locale,
                variables,
                "user-invitation-otp:" + invitationToken + ":" + otpCode,
                invitationToken,
                SUBJECT_OTP,
                Map.of("invitationToken", invitationToken, "email", email),
                () -> renderLegacy(legacyOtpBody,
                        "Dear user, your OTP code is {{otp}}.",
                        Map.of("otp", otpCode))));
    }

    // ─── plumbing ─────────────────────────────────────────────────────────

    /**
     * All-or-nothing dispatch. Tries the primary path; on any failure runs the
     * legacy fallback and swallows any exception from IT too so the surrounding
     * command handler always sees a normal return.
     */
    private void dispatch(SendSpec spec) {
        dispatchWithResult(spec);
    }

    /**
     * Variant that reports whether either path succeeded. Used by
     * {@link #sendOtpOrThrow} so the caller can raise a business error on total
     * failure while every other method swallows.
     */
    private boolean dispatchWithResult(SendSpec spec) {
        if (properties().isEnabled() && notificationsApi.isPresent()) {
            try {
                SendNotificationRequest request = buildServiceRequest(spec);
                NotificationResponse response = notificationsApi.get().send(request);
                LOGGER.info("Invitation email sent via Notification Service (template={}, businessKey={}, notificationId={})",
                        spec.templateCode(), spec.businessKey(), response != null ? response.getId() : null);
                return true;
            } catch (ApiException ex) {
                LOGGER.warn("Notification Service send failed (template={}, httpCode={}, message={}), falling back to legacy mail sender for businessKey={}",
                        spec.templateCode(), ex.getCode(), ex.getMessage(), spec.businessKey());
            } catch (RuntimeException ex) {
                LOGGER.warn("Notification Service unavailable, falling back to legacy mail sender for template={} businessKey={}",
                        spec.templateCode(), spec.businessKey(), ex);
            }
        }
        return sendLegacyFallback(spec);
    }

    private SendNotificationRequest buildServiceRequest(SendSpec spec) {
        NotificationServiceProperties.Invitation invitationProps = properties().getInvitation();

        TemplateRefDto template = new TemplateRefDto();
        template.setCode(spec.templateCode());
        template.setLocale((spec.locale() != null && !spec.locale().isBlank())
                ? spec.locale() : invitationProps.getDefaultLocale());

        RecipientDto recipient = new RecipientDto();
        recipient.setEmail(spec.recipientEmail());

        SendNotificationRequest request = new SendNotificationRequest();
        request.setChannelStrategy(ChannelStrategy.SPECIFIC);
        request.setChannels(List.of(Channel.EMAIL));
        request.setTemplate(template);
        request.setRecipients(List.of(recipient));
        request.setVariables(spec.variables());
        request.setIdempotencyKey(spec.idempotencyKey());
        request.setBusinessKey(spec.businessKey());
        return request;
    }

    private boolean sendLegacyFallback(SendSpec spec) {
        Notification notification = new Notification();
        notification.setRecipients(List.of(spec.recipientEmail()));
        notification.setSubject(spec.legacySubject());
        notification.setContent(spec.legacyBody().get());
        notification.setMetadata(spec.legacyMetadata());
        try {
            NotificationResult result = legacyAdapter.send(notification);
            return result != null && result.isSuccess();
        } catch (Exception ex) {
            LOGGER.error("Legacy mail fallback failed for template={} businessKey={}",
                    spec.templateCode(), spec.businessKey(), ex);
            return false;
        }
    }

    // ─── shared helpers ───────────────────────────────────────────────────

    private Map<String, Object> commonInvitationVariables(String user, String invitationLink) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("project", properties().getInvitation().getProject());
        variables.put("user", user);
        variables.put("link", invitationLink);
        variables.put("year", String.valueOf(Year.now().getValue()));
        String appCenterUrl = properties().getInvitation().getAppCenterUrl();
        if (appCenterUrl != null && !appCenterUrl.isBlank()) {
            variables.put("appCenterUrl", appCenterUrl);
        }
        return variables;
    }

    private static String fallbackDisplayName(String displayName, String email) {
        return (displayName != null && !displayName.isBlank()) ? displayName : email;
    }

    /**
     * Render a legacy template by substituting the given variables. The legacy
     * templates use {@code {{name}}} placeholders — a proper Handlebars renderer
     * is overkill here since the primary path handles proper templating; the
     * fallback just needs a readable email that mentions the user and any
     * event-specific values. Falls back to a hard-coded default when the
     * configured template is blank so a misconfigured env var never emits an
     * empty email.
     */
    private static String renderLegacy(String configured, String hardcodedDefault, Map<String, String> variables) {
        String template = (configured == null || configured.isBlank()) ? hardcodedDefault : configured;
        String rendered = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return rendered;
    }

    /** Bag of the request bits shared between the primary + fallback paths. */
    private record SendSpec(
            String templateCode,
            String recipientEmail,
            String locale,
            Map<String, Object> variables,
            String idempotencyKey,
            String businessKey,
            String legacySubject,
            Map<String, String> legacyMetadata,
            Supplier<String> legacyBody) {}
}
