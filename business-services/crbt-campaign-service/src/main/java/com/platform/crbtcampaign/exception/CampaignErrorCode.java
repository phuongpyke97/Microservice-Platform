package com.platform.crbtcampaign.exception;

import com.platform.common.core.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CampaignErrorCode implements ErrorCode {
    CAMPAIGN_NOT_FOUND("CAMPAIGN_NOT_FOUND", "Campaign not found", HttpStatus.NOT_FOUND),
    CAMPAIGN_PACKAGE_NOT_FOUND("CAMPAIGN_PACKAGE_NOT_FOUND", "Package not found", HttpStatus.NOT_FOUND),
    CAMPAIGN_SUBSCRIPTION_NOT_FOUND("CAMPAIGN_SUBSCRIPTION_NOT_FOUND", "Subscription not found", HttpStatus.NOT_FOUND),
    CAMPAIGN_INSUFFICIENT_CREDIT("CAMPAIGN_INSUFFICIENT_CREDIT", "Insufficient credits to generate music", HttpStatus.PAYMENT_REQUIRED),
    CAMPAIGN_FILE_UPLOAD_FAILED("CAMPAIGN_FILE_UPLOAD_FAILED", "Failed to upload generated audio", HttpStatus.INTERNAL_SERVER_ERROR),
    ALREADY_SUBSCRIBED("ALREADY_SUBSCRIBED", "Already subscribed to this package", HttpStatus.BAD_REQUEST),
    ACTIVE_SUBSCRIPTION_EXISTS("ACTIVE_SUBSCRIPTION_EXISTS", "Active subscription already exists. Confirm change to switch.", HttpStatus.BAD_REQUEST),
    SUBSCRIBER_NOT_ACTIVE("SUBSCRIBER_NOT_ACTIVE", "Subscriber is not active", HttpStatus.BAD_REQUEST),
    INSUFFICIENT_TOKENS("INSUFFICIENT_TOKENS", "Insufficient tokens to perform AI task", HttpStatus.PAYMENT_REQUIRED);

    private final String code;
    private final String message;
    private final HttpStatus status;

    CampaignErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public HttpStatus status() { return status; }
}
