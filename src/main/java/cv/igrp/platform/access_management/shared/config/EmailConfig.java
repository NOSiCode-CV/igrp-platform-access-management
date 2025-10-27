package cv.igrp.platform.access_management.shared.config;

import cv.igrp.framework.notifications.core.adapter.NotificationAdapter;
import cv.igrp.framework.notifications.core.model.NotificationResult;
import cv.igrp.platform.access_management.notification.domain.service.MailAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
@ConditionalOnProperty(
        name = "igrp.notifications.enabled",
        havingValue = "mail"
)
public class EmailConfig {

    @Value("${igrp.mail.host:localhost}")
    private String host;

    @Value("${igrp.mail.port:25}")
    private int port;

    @Value("${igrp.mail.username:}")
    private String username;

    @Value("${igrp.mail.password:}")
    private String password;

    @Value("${igrp.mail.auth:false}")
    private boolean auth;

    @Value("${igrp.mail.starttls.enable:false}")
    private boolean starttls;

    @Value("${igrp.mail.properties.sender-id}")
    private String senderId;

    @Value("${igrp.mail.debug:false}")
    private boolean debug;

    @Bean
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(username);
        mailSender.setPassword(password);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", auth);
        props.put("mail.smtp.starttls.enable", starttls);
        props.put("mail.smtp.from", senderId != null && !senderId.isEmpty() ? senderId : username);
        props.put("mail.debug", debug);

        return mailSender;
    }

    @Bean
    @ConditionalOnMissingBean(NotificationAdapter.class)
    public NotificationAdapter<NotificationResult> notificationAdapter(JavaMailSender javaMailSender) {
        return new MailAdapter(javaMailSender, senderId);
    }

}