package com.platform.crbtcampaign.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "campaign_packages")
public class CampaignPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "credit_amount", nullable = false)
    private int creditAmount;

    @Column(name = "validity_days", nullable = false)
    private int validityDays;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CampaignPackage() {}

    public CampaignPackage(Campaign campaign, String name, BigDecimal price, int creditAmount, int validityDays) {
        this.campaign = campaign;
        this.name = name;
        this.price = price;
        this.creditAmount = creditAmount;
        this.validityDays = validityDays;
    }

    public Long getId() { return id; }
    public Campaign getCampaign() { return campaign; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public int getCreditAmount() { return creditAmount; }
    public int getValidityDays() { return validityDays; }
    public Instant getCreatedAt() { return createdAt; }
}
