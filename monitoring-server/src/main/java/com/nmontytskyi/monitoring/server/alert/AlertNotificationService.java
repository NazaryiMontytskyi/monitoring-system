package com.nmontytskyi.monitoring.server.alert;

import com.nmontytskyi.monitoring.server.config.AlertProperties;
import com.nmontytskyi.monitoring.server.entity.AlertEventEntity;
import com.nmontytskyi.monitoring.server.entity.AlertRuleEntity;
import com.nmontytskyi.monitoring.server.entity.RegisteredServiceEntity;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertNotificationService {

    private final JavaMailSender javaMailSender;
    private final AlertProperties alertProperties;

    public void sendAlert(AlertEventEntity event,
                          AlertRuleEntity rule,
                          RegisteredServiceEntity service) {
        String to = alertProperties.getNotificationTo();
        if (to == null || to.isBlank()) {
            log.warn("Alert notification skipped: notificationTo is not configured");
            return;
        }

        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(alertProperties.getNotificationFrom());
            helper.setTo(to.split(","));
            helper.setSubject(String.format("[MONITORING ALERT] %s — %s threshold exceeded",
                    service.getName(), rule.getMetricType()));

            String body = buildHtmlBody(event, rule, service);
            helper.setText(body, true);

            javaMailSender.send(message);
            log.info("Alert email sent for service {} to {}", service.getName(), to);
        } catch (Exception e) {
            log.error("Failed to send alert email for service {}: {}", service.getName(), e.getMessage(), e);
        }
    }

    private String buildHtmlBody(AlertEventEntity event, AlertRuleEntity rule, RegisteredServiceEntity service) {
        return """
                <html><body>
                <h2>Monitoring Alert</h2>
                <table border="1" cellpadding="6" cellspacing="0">
                  <tr><td><b>Service</b></td><td>%s</td></tr>
                  <tr><td><b>Current Status</b></td><td>%s</td></tr>
                  <tr><td><b>Metric</b></td><td>%s</td></tr>
                  <tr><td><b>Actual Value</b></td><td>%.2f</td></tr>
                  <tr><td><b>Threshold</b></td><td>%s %.2f</td></tr>
                  <tr><td><b>Fired At</b></td><td>%s</td></tr>
                  <tr><td><b>Message</b></td><td>%s</td></tr>
                </table>
                <p><a href="http://localhost:8080/api/dashboard">Open Dashboard</a></p>
                </body></html>
                """.formatted(
                service.getName(),
                service.getStatus(),
                rule.getMetricType(),
                event.getMetricValue(),
                rule.getComparator(), rule.getThreshold(),
                event.getFiredAt(),
                event.getMessage()
        );
    }
}
