package com.platform.notificationservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

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
}
