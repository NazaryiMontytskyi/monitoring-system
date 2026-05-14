package com.nmontytskyi.monitoring.server.alert;

import com.nmontytskyi.monitoring.model.HealthStatus;
import com.nmontytskyi.monitoring.server.config.AlertProperties;
import com.nmontytskyi.monitoring.server.entity.AlertEventEntity;
import com.nmontytskyi.monitoring.server.entity.AlertRuleEntity;
import com.nmontytskyi.monitoring.server.entity.AlertRuleEntity.Comparator;
import com.nmontytskyi.monitoring.server.entity.AlertRuleEntity.MetricType;
import com.nmontytskyi.monitoring.server.entity.RegisteredServiceEntity;
import com.nmontytskyi.monitoring.server.service.AppSettingsService;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertNotificationServiceTest {

    @Mock private JavaMailSender javaMailSender;
    @Mock private AlertProperties alertProperties;
    @Mock private AppSettingsService appSettingsService;

    @InjectMocks
    private AlertNotificationService notificationService;

    private RegisteredServiceEntity service;
    private AlertRuleEntity rule;
    private AlertEventEntity event;

    @BeforeEach
    void setUp() {
        service = RegisteredServiceEntity.builder()
                .id(1L)
                .name("my-service")
                .status(HealthStatus.DOWN)
                .build();

        rule = AlertRuleEntity.builder()
                .id(10L)
                .metricType(MetricType.STATUS_DOWN)
                .comparator(Comparator.GT)
                .threshold(0.5)
                .build();

        event = AlertEventEntity.builder()
                .id(100L)
                .rule(rule)
                .service(service)
                .firedAt(LocalDateTime.now())
                .metricValue(1.0)
                .message("[STATUS_DOWN] Service 'my-service': value=1.00 GT threshold=0.50")
                .build();
    }

    @Test
    void sendAlert_success_callsJavaMailSender() throws Exception {
        when(appSettingsService.get(eq("notification.email.enabled"), any())).thenReturn("true");
        when(appSettingsService.get(eq("notification.email.to"), any())).thenReturn("admin@example.com");
        when(appSettingsService.get(eq("notification.email.from"), any())).thenReturn("monitoring@example.com");
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        notificationService.sendAlert(event, rule, service);

        verify(javaMailSender, times(1)).send(mimeMessage);
    }

    @Test
    void sendAlert_blankNotificationTo_skipsEmail() {
        when(appSettingsService.get(eq("notification.email.enabled"), any())).thenReturn("true");
        when(appSettingsService.get(eq("notification.email.to"), any())).thenReturn("");

        assertThatNoException().isThrownBy(() -> notificationService.sendAlert(event, rule, service));

        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendAlert_mailSenderThrows_doesNotPropagateException() {
        when(appSettingsService.get(eq("notification.email.enabled"), any())).thenReturn("true");
        when(appSettingsService.get(eq("notification.email.to"), any())).thenReturn("admin@example.com");
        when(appSettingsService.get(eq("notification.email.from"), any())).thenReturn("monitoring@example.com");
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("SMTP error")).when(javaMailSender).send(any(MimeMessage.class));

        assertThatNoException().isThrownBy(() -> notificationService.sendAlert(event, rule, service));
    }

    @Test
    void sendAlert_subjectContainsServiceName() throws Exception {
        when(appSettingsService.get(eq("notification.email.enabled"), any())).thenReturn("true");
        when(appSettingsService.get(eq("notification.email.to"), any())).thenReturn("admin@example.com");
        when(appSettingsService.get(eq("notification.email.from"), any())).thenReturn("monitoring@example.com");
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        notificationService.sendAlert(event, rule, service);

        verify(javaMailSender).createMimeMessage();
        verify(javaMailSender).send(mimeMessage);
    }
}
