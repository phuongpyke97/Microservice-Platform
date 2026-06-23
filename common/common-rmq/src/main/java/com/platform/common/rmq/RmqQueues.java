package com.platform.common.rmq;

public final class RmqQueues {

    public static final String USER_REGISTERED = "user-registered-queue";
    public static final String USER_PASSWORD_RESET = "user-password-reset-queue";
    public static final String PAYMENT_RESULT = "payment-result-queue";
    public static final String CREDIT_CHANGED = "credit-changed-queue";
    public static final String AUDIO_GENERATED = "audio-generated-queue";
    public static final String AUDIT_LOG = "audit-log-queue";
    public static final String NOTIFICATION_PAYMENT = "notification-payment-queue";
    public static final String CREDIT_TRANSACTION_HISTORY = "credit-transaction-history-queue";
    public static final String LIBRARY_AUDIO_GENERATED = "library-audio-generated-queue";
    public static final String LYRIA_COST_ALERT = "lyria-cost-alert-queue";

    private RmqQueues() {
    }
}
