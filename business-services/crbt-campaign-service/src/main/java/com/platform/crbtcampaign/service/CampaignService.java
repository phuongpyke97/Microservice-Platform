package com.platform.crbtcampaign.service;

import com.platform.common.core.exception.BaseException;
import com.platform.common.core.exception.CommonErrorCode;
import com.platform.crbtcampaign.client.AuthServiceClient;
import com.platform.crbtcampaign.client.CreditWalletClient;
import com.platform.crbtcampaign.client.dto.UserCreditResponse;
import com.platform.crbtcampaign.client.dto.WalletAmountRequest;
import com.platform.crbtcampaign.dto.request.CreateCampaignRequest;
import com.platform.crbtcampaign.dto.request.CreatePackageRequest;
import com.platform.crbtcampaign.dto.request.RenewSubscriptionRequest;
import com.platform.crbtcampaign.dto.response.CampaignResponse;
import com.platform.crbtcampaign.dto.response.PackageResponse;
import com.platform.crbtcampaign.dto.response.SubscriptionResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final CampaignPackageRepository packageRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final CreditWalletClient creditWalletClient;
    private final AuthServiceClient authServiceClient;

    public CampaignService(CampaignRepository campaignRepository,
                           CampaignPackageRepository packageRepository,
                           UserSubscriptionRepository subscriptionRepository,
                           CreditWalletClient creditWalletClient,
                           AuthServiceClient authServiceClient) {
        this.campaignRepository = campaignRepository;
        this.packageRepository = packageRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.creditWalletClient = creditWalletClient;
        this.authServiceClient = authServiceClient;
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

        // Validate active subscriptions
        List<UserSubscription> activeSubs = subscriptionRepository.findByUserIdAndStatus(userId, UserSubscription.Status.ACTIVE);
        if (!activeSubs.isEmpty()) {
            UserSubscription activeSub = activeSubs.get(0);
            if (activeSub.getCampaignPackage().getId().equals(packageId)) {
                throw new BaseException(CampaignErrorCode.ALREADY_SUBSCRIBED);
            } else {
                throw new BaseException(CampaignErrorCode.ACTIVE_SUBSCRIPTION_EXISTS);
            }
        }

        // Deactivate any CANCELLED subscriptions to clean up database state
        List<UserSubscription> cancelledSubs = subscriptionRepository.findByUserIdAndStatus(userId, UserSubscription.Status.CANCELLED);
        for (UserSubscription cancelledSub : cancelledSubs) {
            cancelledSub.setStatus(UserSubscription.Status.EXPIRED);
            subscriptionRepository.save(cancelledSub);
        }

        Instant expiresAt = Instant.now().plus(pkg.getValidityDays(), ChronoUnit.DAYS);

        UserSubscription subscription = new UserSubscription(
            userId,
            pkg,
            UserSubscription.Status.ACTIVE,
            expiresAt
        );
        subscriptionRepository.save(subscription);

        // Notify Credit Wallet to add credits
        int packageQuota = calculateCreditAmount(pkg);
        var addRequest = new WalletAmountRequest(
            packageQuota,
            "Subscription: " + pkg.getName(),
            "SUB-" + UUID.randomUUID().toString()
        );
        creditWalletClient.add(userId, addRequest);
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
                Long userId = sub.getUserId();

                // 1. Reset credit balance in wallet to 0 first!
                int currentBalance = 0;
                try {
                    var balanceResp = creditWalletClient.getBalance(userId);
                    if (balanceResp != null && balanceResp.success() && balanceResp.data() != null) {
                        currentBalance = balanceResp.data().balance();
                    }
                } catch (Exception e) {
                    System.err.println("Failed to get balance during scheduled renewal: " + e.getMessage());
                }

                if (currentBalance > 0) {
                    try {
                        var deductRequest = new WalletAmountRequest(
                            currentBalance,
                            "Scheduled Renewal Reset",
                            "RESET-" + UUID.randomUUID().toString()
                        );
                        creditWalletClient.deduct(userId, deductRequest);
                    } catch (Exception e) {
                        System.err.println("Failed to deduct old balance during scheduled renewal: " + e.getMessage());
                    }
                }

                // 2. Grant credits
                int creditAmount = calculateCreditAmount(pkg);
                var addRequest = new WalletAmountRequest(
                    creditAmount,
                    "Auto-renewal: " + pkg.getName(),
                    "RENEW-" + UUID.randomUUID().toString()
                );
                creditWalletClient.add(userId, addRequest);

                // 3. Extend expiry
                Instant expiresAt = now.plus(pkg.getValidityDays(), ChronoUnit.DAYS);
                sub.setExpiresAt(expiresAt);
                subscriptionRepository.save(sub);

                renewed++;
            } catch (Exception e) {
                System.err.println("Failed to renew subscription " + sub.getId() + ": " + e.getMessage());
            }
        }

        return renewed;
    }

    @Transactional
    public void unsubscribe(Long userId) {
        List<UserSubscription> activeSubs = subscriptionRepository.findByUserIdAndStatus(userId, UserSubscription.Status.ACTIVE);
        if (activeSubs.isEmpty()) {
            throw new BaseException(CampaignErrorCode.CAMPAIGN_SUBSCRIPTION_NOT_FOUND);
        }
        UserSubscription sub = activeSubs.get(0);
        sub.setStatus(UserSubscription.Status.CANCELLED);
        subscriptionRepository.save(sub);
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getActiveSubscription(Long userId) {
        List<UserSubscription> activeSubs = subscriptionRepository.findByUserIdAndStatus(userId, UserSubscription.Status.ACTIVE);
        if (activeSubs.isEmpty()) {
            throw new BaseException(CampaignErrorCode.CAMPAIGN_SUBSCRIPTION_NOT_FOUND);
        }
        UserSubscription sub = activeSubs.get(0);
        CampaignPackage pkg = sub.getCampaignPackage();

        int balance = 0;
        try {
            var balanceResponse = creditWalletClient.getBalance(userId);
            if (balanceResponse != null && balanceResponse.success() && balanceResponse.data() != null) {
                balance = balanceResponse.data().balance();
            }
        } catch (Exception e) {
            System.err.println("Failed to get wallet balance for user " + userId + ": " + e.getMessage());
        }

        return new SubscriptionResponse(
            sub.getId(),
            pkg.getId(),
            pkg.getName(),
            pkg.getPrice().doubleValue(),
            pkg.getValidityDays(),
            sub.getExpiresAt(),
            balance,
            sub.getExpiresAt(), // tokenExpiredAt matches expiresAt
            sub.getStatus().name(),
            sub.isAutoRenew()
        );
    }

    @Transactional
    public void renewSubscriptionInternal(RenewSubscriptionRequest request) {
        UserCreditResponse userInfo = authServiceClient.getUserCredit(request.msisdn());
        if (userInfo == null || userInfo.userId() == null) {
            throw new BaseException(CommonErrorCode.COMMON_UNAUTHORIZED);
        }
        Long userId = userInfo.userId();

        List<UserSubscription> activeSubs = subscriptionRepository.findByUserIdAndStatus(userId, UserSubscription.Status.ACTIVE);
        if (activeSubs.isEmpty()) {
            throw new BaseException(CampaignErrorCode.CAMPAIGN_SUBSCRIPTION_NOT_FOUND);
        }

        UserSubscription sub = activeSubs.get(0);
        CampaignPackage pkg = sub.getCampaignPackage();

        // 1. Reset credit balance in wallet to 0 first!
        int currentBalance = 0;
        try {
            var balanceResponse = creditWalletClient.getBalance(userId);
            if (balanceResponse != null && balanceResponse.success() && balanceResponse.data() != null) {
                currentBalance = balanceResponse.data().balance();
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch balance during renewal: " + e.getMessage());
        }

        if (currentBalance > 0) {
            try {
                var deductRequest = new WalletAmountRequest(
                    currentBalance,
                    "Renewal Reset",
                    "RESET-" + UUID.randomUUID().toString()
                );
                creditWalletClient.deduct(userId, deductRequest);
            } catch (Exception e) {
                System.err.println("Failed to deduct old balance during renewal: " + e.getMessage());
            }
        }

        // 2. Grant credits
        int creditAmount = calculateCreditAmount(pkg);
        var addRequest = new WalletAmountRequest(
            creditAmount,
            "Renew: " + pkg.getName(),
            "RENEW-" + UUID.randomUUID().toString()
        );
        creditWalletClient.add(userId, addRequest);

        // 3. Update subscription in DB
        sub.setExpiresAt(request.expiresAt());
        subscriptionRepository.save(sub);
    }

    @Transactional
    public int cleanupExpiredTokens() {
        Instant now = Instant.now();
        List<UserSubscription> expired = subscriptionRepository
            .findByStatusInAndExpiresAtBefore(List.of(UserSubscription.Status.ACTIVE, UserSubscription.Status.CANCELLED), now);

        int count = 0;
        for (UserSubscription sub : expired) {
            try {
                Long userId = sub.getUserId();

                // Get current balance
                int currentBalance = 0;
                try {
                    var balanceResp = creditWalletClient.getBalance(userId);
                    if (balanceResp != null && balanceResp.success() && balanceResp.data() != null) {
                        currentBalance = balanceResp.data().balance();
                    }
                } catch (Exception e) {
                    System.err.println("Failed to get balance during cleanup: " + e.getMessage());
                }

                if (currentBalance > 0) {
                    try {
                        var deductRequest = new WalletAmountRequest(
                            currentBalance,
                            "Token expired reset",
                            "RESET-" + UUID.randomUUID().toString()
                        );
                        creditWalletClient.deduct(userId, deductRequest);
                    } catch (Exception e) {
                        System.err.println("Failed to deduct balance during cleanup: " + e.getMessage());
                    }
                }

                sub.setStatus(UserSubscription.Status.EXPIRED);
                subscriptionRepository.save(sub);

                count++;
            } catch (Exception e) {
                System.err.println("Failed to clean up expired sub " + sub.getId() + ": " + e.getMessage());
            }
        }
        return count;
    }
}
