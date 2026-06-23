package com.platform.crbtcampaign.client.dto;

public class UserCreditStats {
    private Long purchased;
    private Long used;

    public UserCreditStats() {
        this.purchased = 0L;
        this.used = 0L;
    }

    public UserCreditStats(Long purchased, Long used) {
        this.purchased = purchased != null ? purchased : 0L;
        this.used = used != null ? used : 0L;
    }

    public Long getPurchased() {
        return purchased;
    }

    public void setPurchased(Long purchased) {
        this.purchased = purchased;
    }

    public Long getUsed() {
        return used;
    }

    public void setUsed(Long used) {
        this.used = used;
    }
}
