package com.platform.audiogeneration.client.dto;

public record WalletAmountRequest(int amount, String reason, String referenceId, Boolean isFree, String genType) {
    public WalletAmountRequest(int amount, String reason, String referenceId) {
        this(amount, reason, referenceId, false, "OTHER");
    }
}
