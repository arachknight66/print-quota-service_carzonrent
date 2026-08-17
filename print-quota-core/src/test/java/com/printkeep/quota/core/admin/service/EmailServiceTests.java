package com.printkeep.quota.core.admin.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class EmailServiceTests {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    @Test
    void testSendEmailWithAttachmentSimulatedWhenMailSenderIsNull() {
        final EmailService service = new EmailService(null, new SimpleMeterRegistry());
        final String[] to = {"admin@company.local"};

        // Should not throw, should log warning and increment success counter
        service.sendEmailWithAttachment(to, "Subject", "Body", null, null);
    }

    @Test
    void testSendEmailWithAttachmentSuccess() {
        final EmailService service = new EmailService(mailSender, new SimpleMeterRegistry());
        final String[] to = {"admin@company.local"};

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        service.sendEmailWithAttachment(to, "Subject", "Body", "file.xlsx", new byte[]{1, 2, 3});

        verify(mailSender, times(1)).createMimeMessage();
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    void testSendEmailWithAttachmentRetriesAndFails() {
        final EmailService service = new EmailService(mailSender, new SimpleMeterRegistry());
        final String[] to = {"admin@company.local"};

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new RuntimeException("SMTP Server Down")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> service.sendEmailWithAttachment(to, "Subject", "Body", null, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("SMTP delivery failed permanently");

        // Verifies the retry logic (3 attempts total)
        verify(mailSender, times(3)).send(mimeMessage);
    }
}
