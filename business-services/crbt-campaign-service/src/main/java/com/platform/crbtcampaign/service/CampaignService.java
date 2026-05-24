package com.platform.crbtcampaign.service;

import com.platform.common.core.exception.BaseException;
import com.platform.common.core.exception.CommonErrorCode;
import com.platform.common.rmq.RmqExchanges;
import com.platform.common.rmq.RmqRoutingKeys;
import com.platform.common.rmq.event.CreditChangedEvent;
import com.platform.crbtcampaign.dto.request.CreateCampaignRequest;
import com.platform.crbtcampaign.dto.request.CreatePackageRequest;
import com.platform.crbtcampaign.dto.response.CampaignResponse;
import com.platform.crbtcampaign.dto.response.PackageResponse;
import com.platform.crbtcampaign.entity.Campaign;
import com.platform.crbtcampaign.entity.CampaignPackage;
import com.platform.crbtcampaign.entity.UserSubscription;
import com.platform.crbtcampaign.exception.CampaignErrorCode;
import com.platform.crbtcampaign.repository.CampaignPackageRepository;
import com.platform.crbtcampaign.repository.CampaignRepository;
import com.platform.crbtcampaign.repository.UserSubscriptionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final CampaignPackageRepository packageRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final RabbitTemplate rabbitTemplate;

    public CampaignService(CampaignRepository campaignRepository,
                           CampaignPackageRepository packageRepository,
                           UserSubscriptionRepository subscriptionRepository,
                           RabbitTemplate rabbitTemplate) {
        this.campaignRepository = campaignRepository;
        this.packageRepository = packageRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    public CampaignResponse createCampaign(CreateCampaignRequest request) {
        Campaign campaign = new Campaign(
            request.name(),
            request.description(),
            Campaign.Status.ACTIVE,
            request.startAt(),
            request.endAt()
        );

        for (CreatePackageRequest pkgReq : request.packages()) {
            campaign.getPackages().add(new CampaignPackage(
                campaign,
                pkgReq.name(),
                pkgReq.price(),
                pkgReq.creditAmount(),
                pkgReq.validityDays()
            ));
        }

        return toCampaignResponse(campaignRepository.save(campaign));
    }

    @Transactional(readOnly = true)
    public List<CampaignResponse> getActiveCampaigns() {
        Instant now = Instant.now();
        return campaignRepository.findByStatusAndStartAtBeforeAndEndAtAfter(Campaign.Status.ACTIVE, now, now)
            .stream()
            .map(this::toCampaignResponse)
            .toList();
    }

    @Transactional
    public void subscribe(Long userId, Long packageId) {
        CampaignPackage pkg = packageRepository.findById(packageId)
            .orElseThrow(() -> new BaseException(CampaignErrorCode.CAMPAIGN_PACKAGE_NOT_FOUND));

        if (pkg.getCampaign().getStatus() != Campaign.Status.ACTIVE ||
            pkg.getCampaign().getEndAt().isBefore(Instant.now())) {
            throw new BaseException(CampaignErrorCode.CAMPAIGN_NOT_FOUND);
        }

        UserSubscription subscription = new UserSubscription(
            userId,
            pkg,
            UserSubscription.Status.ACTIVE,
            Instant.now().plus(pkg.getValidityDays(), ChronoUnit.DAYS)
        );
        subscriptionRepository.save(subscription);

        // Notify Credit Wallet to add credits
        CreditChangedEvent event = new CreditChangedEvent(
            userId,
            calculateCreditAmount(pkg),
            "IN",
            "Subscription: " + pkg.getName(),
            "SUB-" + UUID.randomUUID().toString(),
            System.currentTimeMillis()
        );
        rabbitTemplate.convertAndSend(RmqExchanges.CREDIT_EVENTS, RmqRoutingKeys.CREDIT_CHANGED, event);
    }

    private int calculateCreditAmount(CampaignPackage pkg) {
        // Bonus rule engine (T8.3): +10% for packages > 1000 price
        int base = pkg.getCreditAmount();
        if (pkg.getPrice().doubleValue() >= 1000) {
            return (int) (base * 1.1);
        }
        return base;
    }

    private CampaignResponse toCampaignResponse(Campaign campaign) {
        return new CampaignResponse(
            campaign.getId(),
            campaign.getName(),
            campaign.getDescription(),
            campaign.getStatus(),
            campaign.getStartAt(),
            campaign.getEndAt(),
            campaign.getPackages().stream().map(this::toPackageResponse).toList()
        );
    }

    private PackageResponse toPackageResponse(CampaignPackage pkg) {
        return new PackageResponse(
            pkg.getId(),
            pkg.getName(),
            pkg.getPrice(),
            pkg.getCreditAmount(),
            pkg.getValidityDays()
        );
    }

    /**
     * Auto-renew subscriptions (T8.7)
     * Called by scheduler at 00:00 daily
     */
    @Transactional
    public int renewSubscriptions() {
        Instant now = Instant.now();
        List<UserSubscription> expiring = subscriptionRepository
            .findAllByStatusAndAutoRenewAndExpiresAtBefore(UserSubscription.Status.ACTIVE, true, now);

        int renewed = 0;
        for (UserSubscription sub : expiring) {
            try {
                CampaignPackage pkg = sub.getCampaignPackage();

                // Extend expiry
                sub.setExpiresAt(now.plus(pkg.getValidityDays(), ChronoUnit.DAYS));
                subscriptionRepository.save(sub);

                // Grant credits with bonus
                int creditAmount = calculateCreditAmount(pkg);
                CreditChangedEvent event = new CreditChangedEvent(
                    sub.getUserId(),
                    creditAmount,
                    "IN",
                    "Auto-renewal: " + pkg.getName(),
                    "RENEW-" + UUID.randomUUID().toString(),
                    System.currentTimeMillis()
                );
                rabbitTemplate.convertAndSend(RmqExchanges.CREDIT_EVENTS, RmqRoutingKeys.CREDIT_CHANGED, event);

                renewed++;
            } catch (Exception e) {
                // Log but continue with other subscriptions
                System.err.println("Failed to renew subscription " + sub.getId() + ": " + e.getMessage());
            }
        }

        return renewed;
    }
}
