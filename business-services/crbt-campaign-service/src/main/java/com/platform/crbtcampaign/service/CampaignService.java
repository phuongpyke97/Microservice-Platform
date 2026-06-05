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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class CampaignService {

    private static final Logger log = LoggerFactory.getLogger(CampaignService.class);

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
    public void subscribe(Long userId, Long packageId, boolean confirmChange) {
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
            }
            // Switching to a different package requires explicit confirmation
            // (the client re-sends with confirmChange=true). Without it we surface
            // ACTIVE_SUBSCRIPTION_EXISTS so the UI can prompt. The old subscription
            // is cancelled here; leftover credits are NOT reset — the new package's
            // quota accumulates on top, consistent with re-subscribe semantics.
            if (!confirmChange) {
                throw new BaseException(CampaignErrorCode.ACTIVE_SUBSCRIPTION_EXISTS);
            }
            activeSub.setStatus(UserSubscription.Status.EXPIRED);
            subscriptionRepository.save(activeSub);
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

        // Accumulate, do NOT reset: credits only expire at the package's expiry
        // (cleanupExpiredTokens) or at the renewal boundary. A user who cancels
        // mid-period keeps the leftover credits, so re-subscribing adds the new
        // quota on top (e.g. 2 leftover + 3 quota = 5), riding the new expiry.
        //
        // Granted only AFTER the subscription is durably committed — running the
        // remote Feign call inside the tx would hold the DB connection for the whole
        // network round-trip and risk a credit/subscription mismatch on rollback.
        // Strings are built eagerly because the entity may be detached by afterCommit.
        int packageQuota = calculateCreditAmount(pkg);
        String grantReason = "Subscription: " + pkg.getName();
        runAfterCommit(() -> grantCredits(userId, packageQuota, grantReason, "SUB-"));
    }

    /** Run an action after the surrounding tx commits, or inline if no tx is active. */
    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /**
     * Renewal credit rule (auto-renew + telco-initiated renew): reset the wallet to
     * zero at the expiry boundary, then grant the fresh {@code quota} — leftover
     * credits do not roll past a renewal. Distinct from a mid-period re-subscribe,
     * which accumulates via {@link #grantCredits}.
     */
    private void resetThenGrantCredits(Long userId, int quota, String grantReason, String refPrefix) {
        resetCreditsToZero(userId);
        grantCredits(userId, quota, grantReason, refPrefix);
    }

    /**
     * Add {@code quota} credits on top of the current balance (no reset). Used by
     * subscribe/re-subscribe so leftover credits from a cancelled-but-not-expired
     * package accumulate. Failures are logged for out-of-band reconciliation rather
     * than rethrown — the subscription already stands and cannot be undone here.
     */
    private void grantCredits(Long userId, int quota, String grantReason, String refPrefix) {
        try {
            creditWalletClient.add(userId, new WalletAmountRequest(
                quota, grantReason, refPrefix + UUID.randomUUID().toString()));
        } catch (Exception e) {
            log.error("[CREDIT] Grant failed: userId={}, ref={}, reason={}",
                userId, refPrefix, e.getMessage(), e);
        }
    }

    /**
     * Drain the wallet to zero. Shared by the reset-then-grant rule and by expiry
     * cleanup. Each wallet call is isolated and failures are logged, not rethrown,
     * for out-of-band reconciliation.
     */
    private void resetCreditsToZero(Long userId) {
        try {
            var balanceResp = creditWalletClient.getBalance(userId);
            if (balanceResp != null && balanceResp.success() && balanceResp.data() != null
                    && balanceResp.data().balance() > 0) {
                creditWalletClient.deduct(userId, new WalletAmountRequest(
                    balanceResp.data().balance(),
                    "Reset balance to zero",
                    "RESET-" + UUID.randomUUID().toString()
                ));
            }
        } catch (Exception e) {
            log.error("[CREDIT] Balance reset failed: userId={}, reason={}", userId, e.getMessage(), e);
        }
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

                // Same reset-then-grant rule as subscribe(), so a renewed package
                // leaves the same balance as a fresh subscription to it.
                int creditAmount = calculateCreditAmount(pkg);
                resetThenGrantCredits(userId, creditAmount, "Auto-renewal: " + pkg.getName(), "RENEW-");

                // Extend expiry
                Instant expiresAt = now.plus(pkg.getValidityDays(), ChronoUnit.DAYS);
                sub.setExpiresAt(expiresAt);
                subscriptionRepository.save(sub);

                renewed++;
            } catch (Exception e) {
                log.error("Failed to renew subscription {}: {}", sub.getId(), e.getMessage(), e);
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
            log.error("Failed to get wallet balance for user {}: {}", userId, e.getMessage(), e);
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

        // Same reset-then-grant rule as subscribe() and the scheduled renewal.
        int creditAmount = calculateCreditAmount(pkg);
        resetThenGrantCredits(userId, creditAmount, "Renew: " + pkg.getName(), "RENEW-");

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

                // Expired token → drain any leftover balance to zero.
                resetCreditsToZero(userId);

                sub.setStatus(UserSubscription.Status.EXPIRED);
                subscriptionRepository.save(sub);

                count++;
            } catch (Exception e) {
                log.error("Failed to clean up expired sub {}: {}", sub.getId(), e.getMessage(), e);
            }
        }
        return count;
    }
}
