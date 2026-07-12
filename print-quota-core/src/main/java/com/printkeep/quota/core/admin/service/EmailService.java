package com.printkeep.quota.core.admin.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Service facilitating SMTP email communications, HTML rendering, and attachments dispatch.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final Counter emailsSent;
    private final Counter emailFailures;

    public EmailService(
            @Autowired(required = false) final JavaMailSender mailSender,
            final MeterRegistry registry) {
        this.mailSender = mailSender;
        this.emailsSent = Counter.builder("email.sent")
                .description("Total successfully dispatched emails")
                .register(registry);
        this.emailFailures = Counter.builder("email.failures")
                .description("Total failed email attempts")
                .register(registry);
    }

    /**
     * Sends an HTML email with optional binary attachments.
     */
    public void sendEmailWithAttachment(
            final String[] to,
            final String subject,
            final String htmlBody,
            final String attachmentName,
            final byte[] attachmentBytes) {

        if (mailSender == null) {
            log.warn("JavaMailSender is not configured. Simulating email dispatch to: {}", String.join(", ", to));
            emailsSent.increment();
            return;
        }

        int attempts = 0;
        final int maxAttempts = 3;

        while (true) {
            attempts++;
            try {
                final MimeMessage message = mailSender.createMimeMessage();
                final MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(htmlBody, true);
                helper.setFrom("printkeep-noreply@company.local");

                if (attachmentName != null && attachmentBytes != null) {
                    helper.addAttachment(attachmentName, new ByteArrayResource(attachmentBytes));
                }

                mailSender.send(message);
                log.info("Successfully sent email alert: '{}' to recipients", subject);
                emailsSent.increment();
                break;
            } catch (final Exception e) {
                log.error("Failed to send email alert '{}' on attempt {}/{}", subject, attempts, maxAttempts, e);
                if (attempts >= maxAttempts) {
                    emailFailures.increment();
                    throw new RuntimeException("SMTP delivery failed permanently for alert: " + subject, e);
                }
                try {
                    Thread.sleep(1000);
                } catch (final InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Email dispatch interrupted", ie);
                }
            }
        }
    }
}
