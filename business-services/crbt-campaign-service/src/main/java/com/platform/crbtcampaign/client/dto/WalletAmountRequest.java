package com.platform.crbtcampaign.client.dto;

public record WalletAmountRequest(int amount, String reason, String referenceId, Boolean isFree, String genType, String model) {
    public WalletAmountRequest(int amount, String reason, String referenceId, Boolean isFree, String genType) {
        this(amount, reason, referenceId, isFree, genType, null);
    }

    public WalletAmountRequest(int amount, String reason, String referenceId) {
        this(amount, reason, referenceId, false, "OTHER", null);
    }
}
