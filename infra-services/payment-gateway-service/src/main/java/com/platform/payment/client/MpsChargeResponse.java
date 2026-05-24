package com.platform.payment.client;

public record MpsChargeResponse(boolean success, String providerReference, String message) {
}
