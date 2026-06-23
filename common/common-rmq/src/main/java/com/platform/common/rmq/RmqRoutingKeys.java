package com.platform.common.rmq;

public final class RmqRoutingKeys {

    public static final String USER_REGISTERED = "user.registered";
    public static final String USER_PASSWORD_RESET = "user.password.reset";
    public static final String PAYMENT_RESULT = "payment.result";
    public static final String PAYMENT_SUCCESS = "payment.success";
    public static final String CREDIT_CHANGED = "credit.changed";
    public static final String CREDIT_DEDUCTED = "credit.deducted";
    public static final String AUDIO_GENERATED = "audio.generated";
    public static final String AUDIO_COMPLETED = "audio.completed";
    public static final String AUDIT_LOG = "audit.log";
    public static final String LYRIA_COST_ALERT = "lyria.cost.alert";

    private RmqRoutingKeys() {
    }
}
