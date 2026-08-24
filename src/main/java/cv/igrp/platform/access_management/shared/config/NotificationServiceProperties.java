package cv.igrp.platform.access_management.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the primary user-invitation delivery path: the iGRP
 * Notification Service (its Java client). All values are optional so
 * disabling the primary path only requires
 * {@code igrp.notification.service.enabled=false}; on that switch the
 * invitation flow reverts to the legacy Spring Mail sender that is still
 * wired via {@link cv.igrp.platform.access_management.shared.config.EmailConfig}.
 *
 * <p>The Notification Service resolves the template by the
 * {@code application_code} claim of the M2M token, so the client credentials
 * configured here must belong to a service account registered under
 * {@code APP_IGRP_CENTER} with the {@code igrp.notification.send} permission.
 * The template code + locale + Handlebars variables are supplied at send time
 * from {@link #getInvitation()}.
 */
@ConfigurationProperties(prefix = "igrp.notification.service")
public class NotificationServiceProperties {

    /** Master switch. When {@code false} the invitation flow bypasses the client entirely. */
    private boolean enabled = false;

    /** Base URL of the Notification Service (e.g. {@code https://api-gateway/notification-service}). */
    private String baseUrl;

    /**
     * Base URL of the OAuth2 authorization server issuing the M2M token
     * (e.g. {@code https://api-gateway/igrp-access-management}). The client
     * library will POST {@code {authUrl}/oauth2/token} internally.
     */
    private String authUrl;

    /** OAuth2 {@code client_credentials} client id (registered under APP_IGRP_CENTER). */
    private String clientId;

    /** OAuth2 client secret matching {@link #clientId}. */
    private String clientSecret;

    /** Optional token scope. Leave blank when the client registration is unconstrained. */
    private String scope;

    /** Per-notification (template + variables) configuration. */
    private final Invitation invitation = new Invitation();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getAuthUrl() { return authUrl; }
    public void setAuthUrl(String authUrl) { this.authUrl = authUrl; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public Invitation getInvitation() { return invitation; }

    /**
     * Per-template settings for the user-invitation flow. Kept as a nested
     * type so future template-driven flows can add sibling nested types
     * (e.g. {@code getPasswordReset()}) without polluting the outer
     * property namespace.
     */
    public static class Invitation {
        /** Template code used when a new invitation is issued. Defaults to the seed name. */
        private String templateCode = "user-invitation";

        /**
         * Template code used when an existing invitation is resent. Defaults to the
         * same template as the initial invite because the email content is identical —
         * only the trigger differs. Override to a distinct template only if operations
         * later publishes a separate "user-invitation-resend" template.
         */
        private String resendTemplateCode = "user-invitation";

        /**
         * Template code used to notify the invitee that their pending invitation was
         * cancelled by an administrator. Not yet seeded by the Notification Service —
         * until a matching template lands on that side, sends will 4xx and fall back
         * to the legacy Spring Mail body ({@code IGRP_MAIL_INVITE_CANCELLATION_TEMPLATE}).
         */
        private String cancellationTemplateCode = "user-invitation-cancelled";

        /**
         * Template code used to confirm the invitee that their acceptance succeeded.
         * Same seed-not-yet-published caveat as {@link #cancellationTemplateCode}.
         */
        private String responseTemplateCode = "user-invitation-responded";

        /**
         * Template code used to email the OTP during invitation email validation
         * ({@link cv.igrp.platform.access_management.users.application.commands.ValidateInvitationEmailCommandHandler}).
         * Same seed-not-yet-published caveat as {@link #cancellationTemplateCode}.
         */
        private String otpTemplateCode = "user-invitation-otp";

        /** Fallback locale when the invited user's own locale is unknown. */
        private String defaultLocale = "en";

        /** Handlebars {@code {{project}}} value baked into every invitation email. */
        private String project = "iGRP";

        /**
         * Handlebars {@code {{appCenterUrl}}} value. Optional — omit to skip
         * that variable in the rendered email (the template silently no-ops
         * missing optional variables).
         */
        private String appCenterUrl;

        public String getTemplateCode() { return templateCode; }
        public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }

        public String getResendTemplateCode() { return resendTemplateCode; }
        public void setResendTemplateCode(String resendTemplateCode) { this.resendTemplateCode = resendTemplateCode; }

        public String getCancellationTemplateCode() { return cancellationTemplateCode; }
        public void setCancellationTemplateCode(String cancellationTemplateCode) { this.cancellationTemplateCode = cancellationTemplateCode; }

        public String getResponseTemplateCode() { return responseTemplateCode; }
        public void setResponseTemplateCode(String responseTemplateCode) { this.responseTemplateCode = responseTemplateCode; }

        public String getOtpTemplateCode() { return otpTemplateCode; }
        public void setOtpTemplateCode(String otpTemplateCode) { this.otpTemplateCode = otpTemplateCode; }

        public String getDefaultLocale() { return defaultLocale; }
        public void setDefaultLocale(String defaultLocale) { this.defaultLocale = defaultLocale; }

        public String getProject() { return project; }
        public void setProject(String project) { this.project = project; }

        public String getAppCenterUrl() { return appCenterUrl; }
        public void setAppCenterUrl(String appCenterUrl) { this.appCenterUrl = appCenterUrl; }
    }
}
