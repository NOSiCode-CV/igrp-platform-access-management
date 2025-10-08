package cv.igrp.platform.access_management.notification.domain.service;

import cv.igrp.framework.notifications.core.adapter.NotificationAdapter;
import cv.igrp.framework.notifications.core.exception.NotificationException;
import cv.igrp.framework.notifications.core.model.Notification;
import cv.igrp.framework.notifications.core.model.NotificationAttachment;
import cv.igrp.framework.notifications.core.model.NotificationResult;
import jakarta.activation.DataSource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Local email adapter that sends notifications using Spring's {@link JavaMailSender}.
 * <p>
 * This implementation relies entirely on Spring Mail autoconfiguration (via
 * {@code spring.mail.*} properties in the application configuration) and does
 * not require manual setup of SMTP configuration objects.
 * <p>
 * It supports plain text and HTML emails, as well as file attachments.
 */
public class MailAdapter implements NotificationAdapter<NotificationResult> {

    private final Logger LOGGER = LoggerFactory.getLogger(MailAdapter.class);

    private final JavaMailSender mailSender;
    private final String sender;

    /**
     * Constructs a MailAdapter using the injected {@link JavaMailSender} bean.
     *
     * @param mailSender the Spring-managed JavaMailSender instance
     */
    public MailAdapter(JavaMailSender mailSender, String sender) {
        this.mailSender = mailSender;
        this.sender = sender;
    }

    /**
     * Sends a notification using Spring's JavaMailSender.
     * <p>
     * Automatically handles HTML or plain text messages and adds attachments if present.
     * Returns a {@link NotificationResult} describing whether the operation succeeded.
     *
     * @param notification the notification to send
     * @return a {@link NotificationResult} indicating the outcome of the operation
     * @throws IllegalStateException if the adapter is not properly configured
     * @throws NotificationException if a fatal error occurs while sending
     */
    @Override
    public NotificationResult send(Notification notification) throws IllegalStateException, NotificationException {
        NotificationResult response = new NotificationResult();

        String emailsToSend = String.join(", ", notification.getRecipients());

        try {
            MimeMessage message = mailSender.createMimeMessage();
            boolean multipart = notification.getAttachments() != null && !notification.getAttachments().isEmpty();
            MimeMessageHelper helper = new MimeMessageHelper(message, multipart, "utf-8");

            // FROM (uses Spring Mail autoconfiguration for username)
            helper.setFrom(sender != null ? sender
                    : mailSender.createMimeMessage().getFrom() != null
                    ? message.getFrom()[0].toString()
                    : "");

            // TO
            List<String> recipients = notification.getRecipients();
            if (recipients == null || recipients.isEmpty()) {
                throw new IllegalArgumentException("No recipients provided.");
            }
            helper.setTo(recipients.toArray(new String[0]));

            // SUBJECT
            helper.setSubject(notification.getSubject() != null ? notification.getSubject() : "");

            // CONTENT
            if (isHtml(notification.getContent())) {
                helper.setText(notification.getContent(), true);
            } else {
                helper.setText(notification.getContent() != null ? notification.getContent() : "", false);
            }

            // ATTACHMENTS
            if (notification.getAttachments() != null) {
                for (NotificationAttachment att : notification.getAttachments()) {
                    if (att != null && att.getContent() != null &&
                            att.getFilename() != null && att.getMimeType() != null) {
                        try {
                            DataSource source = new ByteArrayDataSource(att.getContent(), att.getMimeType());
                            helper.addAttachment(att.getFilename(), source);
                        } catch (Exception ex) {
                            throw new NotificationException("An error occurred while processing the attachments", ex);
                        }
                    }
                }
            }

            LOGGER.info("Sending email to {}...", emailsToSend);

            mailSender.send(message);

            response.setSuccess(true);
            response.setTimestamp(Instant.now());
            response.setMessage("Email sent successfully.");

            LOGGER.info("Email sent successfully to {}", emailsToSend);

        } catch (MessagingException | IllegalArgumentException e) {
            response.setSuccess(false);
            response.setTimestamp(Instant.now());
            response.setMessage("Error sending email: " + e.getMessage());
            LOGGER.warn("Failed to send email to {}", emailsToSend, e);
        } catch (Exception e) {
            response.setSuccess(false);
            response.setTimestamp(Instant.now());
            response.setMessage("Unexpected error while sending email: " + e.getMessage());
            LOGGER.warn("Unexpected error while sending email to {}", emailsToSend, e);
        }

        return response;
    }

    /**
     * Detects whether the provided string content is HTML-based.
     *
     * @param content the message content to inspect
     * @return true if it appears to be HTML; false otherwise
     */
    private static boolean isHtml(String content) {
        if (content == null || content.trim().isEmpty()) return false;
        String trimmed = content.trim().toLowerCase();
        return trimmed.startsWith("<html") ||
                trimmed.startsWith("<!doctype html") ||
                trimmed.contains("<body") ||
                (trimmed.contains("<p>") && trimmed.contains("</p>")) ||
                (trimmed.contains("<div") && trimmed.contains("</div"));
    }
}
