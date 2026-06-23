package com.platform.notificationservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final JavaMailSender mailSender;
    private String emailSubjectTemplate;
    private String emailBodyTemplate;

    public NotificationService(ObjectMapper objectMapper, ResourceLoader resourceLoader, JavaMailSender mailSender) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.mailSender = mailSender;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        try {
            Resource resource = resourceLoader.getResource("classpath:Email_Template.json");
            try (InputStream is = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(is);
                this.emailSubjectTemplate = root.path("subject").asText();
                this.emailBodyTemplate = root.path("body").asText();
            }
            log.info("[NOTIFICATION-INIT] Loaded Email_Template.json successfully");
        } catch (Exception e) {
            log.error("[NOTIFICATION-INIT-FAILED] Failed to load Email_Template.json, using defaults", e);
            this.emailSubjectTemplate = "CẢNH BÁO: Chi phí Lyria vượt ngưỡng";
            this.emailBodyTemplate = "Ngày: ${statDate}, Chi phí: ${currentCost}, Ngưỡng: ${thresholdCost}";
        }
    }

    public void sendWelcome(String email, String msisdn) {
        log.info("Send welcome notification email={} msisdn={}", email, msisdn);
    }

    public void sendOtp(String email, String otp) {
        log.info("Send password reset otp email={} otp={}", email, otp);
    }

    public void sendPaymentConfirmation(Long userId, String packageCode, String status) {
        log.info("Send payment confirmation userId={} packageCode={} status={}", userId, packageCode, status);
    }

    public void sendAudioReady(Long userId, String jobId, String audioUrl) {
        log.info("Send audio ready userId={} jobId={} audioUrl={}", userId, jobId, audioUrl);
    }

    public void sendLyriaCostAlert(String email, java.math.BigDecimal threshold, java.math.BigDecimal currentCost, String date) {
        String subject = emailSubjectTemplate;
        String body = emailBodyTemplate;

        if (subject != null) {
            subject = subject.replace("${statDate}", date)
                             .replace("${thresholdCost}", threshold.toString())
                             .replace("${currentCost}", currentCost.toString())
                             .replace("${recipientEmail}", email);
        }
        if (body != null) {
            body = body.replace("${statDate}", date)
                       .replace("${thresholdCost}", threshold.toString())
                       .replace("${currentCost}", currentCost.toString())
                       .replace("${recipientEmail}", email);
        }

        log.warn("=== SENDING LYRIA DAILY COST ALERT EMAIL ===");
        log.warn("To: {}", email);
        log.warn("Subject: {}", subject);
        log.warn("Body:\n{}", body);
        log.warn("=============================================");

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(body, false); // plain text
            
            // Extract from address from mail username or default
            helper.setFrom("ai-crbt-system@platform.com");
            
            mailSender.send(message);
            log.info("[EMAIL-SUCCESS] Real cost alert email sent successfully to: {}", email);
        } catch (Exception e) {
            log.error("[EMAIL-FAILED] Failed to send real cost alert email to: {}", email, e);
        }
    }
}
