package com.platform.crbtcampaign.service;

import com.platform.common.core.response.PageResponse;
import com.platform.crbtcampaign.client.AuthServiceClient;
import com.platform.crbtcampaign.client.CreditWalletClient;
import com.platform.crbtcampaign.client.CreditTransactionClient;
import com.platform.crbtcampaign.client.dto.UserResponse;
import com.platform.crbtcampaign.client.dto.UserCreditStats;
import com.platform.crbtcampaign.dto.response.UserCreditSummaryResponse;
import com.platform.crbtcampaign.dto.response.UserCreditSummaryStats;
import com.platform.crbtcampaign.dto.response.UserCreditSummaryPageWithStats;
import com.platform.crbtcampaign.entity.UserSubscription;
import com.platform.crbtcampaign.repository.UserSubscriptionRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AdminUserService {

    private final AuthServiceClient authServiceClient;
    private final CreditWalletClient creditWalletClient;
    private final CreditTransactionClient creditTransactionClient;
    private final UserSubscriptionRepository userSubscriptionRepository;

    private static final DateTimeFormatter DATE_FORMATTER = 
        DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    public AdminUserService(
            AuthServiceClient authServiceClient,
            CreditWalletClient creditWalletClient,
            CreditTransactionClient creditTransactionClient,
            UserSubscriptionRepository userSubscriptionRepository) {
        this.authServiceClient = authServiceClient;
        this.creditWalletClient = creditWalletClient;
        this.creditTransactionClient = creditTransactionClient;
        this.userSubscriptionRepository = userSubscriptionRepository;
    }

    public UserCreditSummaryPageWithStats getUsersCreditSummary(
            String msisdn, String status, String packageName, String startTime, String endTime, int page, int size) {
        
        // 1. Fetch paginated users from Auth Service
        PageResponse<UserResponse> authUsersPage = authServiceClient.getUsers(msisdn, status, startTime, endTime, page, size);
        if (authUsersPage == null || authUsersPage.content() == null || authUsersPage.content().isEmpty()) {
            return new UserCreditSummaryPageWithStats(
                PageResponse.from(new org.springframework.data.domain.PageImpl<>(
                    Collections.emptyList(), 
                    org.springframework.data.domain.PageRequest.of(page, size), 
                    0
                )),
                new UserCreditSummaryStats(0, 0, 0, 0, 0)
            );
        }

        List<UserResponse> authUsers = authUsersPage.content();
        List<Long> userIds = authUsers.stream().map(UserResponse::id).collect(Collectors.toList());

        // 2. Fetch credit balances from Wallet Service in bulk
        Map<Long, Integer> balancesMap = new HashMap<>();
        try {
            var balancesResp = creditWalletClient.getBalances(userIds);
            if (balancesResp != null && balancesResp.data() != null) {
                balancesResp.data().forEach((k, v) -> {
                    try {
                        Long key = Long.valueOf(String.valueOf(k));
                        balancesMap.put(key, v);
                    } catch (Exception ex) {
                        // ignore
                    }
                });
            }
        } catch (Exception e) {
            // Log or fallback silently
        }

        // 3. Fetch purchased/used credit stats from Credit Transaction Service in bulk
        Map<Long, UserCreditStats> statsMap = new HashMap<>();
        try {
            var statsResp = creditTransactionClient.getStats(userIds);
            if (statsResp != null && statsResp.data() != null) {
                statsResp.data().forEach((k, v) -> {
                    try {
                        Long key = Long.valueOf(String.valueOf(k));
                        statsMap.put(key, v);
                    } catch (Exception ex) {
                        // ignore
                    }
                });
            }
        } catch (Exception e) {
            // Log or fallback silently
        }

        // 4. Fetch subscriptions from local DB in bulk
        List<UserSubscription> subscriptions = userSubscriptionRepository.findByUserIds(userIds);
        Map<Long, List<UserSubscription>> userSubsMap = subscriptions.stream()
            .collect(Collectors.groupingBy(UserSubscription::getUserId));

        // 5. Aggregate into UserCreditSummaryResponse
        List<UserCreditSummaryResponse> content = new ArrayList<>();
        for (UserResponse user : authUsers) {
            Long uId = user.id();
            Integer remaining = balancesMap.getOrDefault(uId, 0);
            UserCreditStats stats = statsMap.getOrDefault(uId, new UserCreditStats(0L, 0L));
            
            // Find active or latest subscription
            List<UserSubscription> userSubs = userSubsMap.getOrDefault(uId, Collections.emptyList());
            UserSubscription selectedSub = userSubs.stream()
                .filter(s -> s.getStatus() == UserSubscription.Status.ACTIVE)
                .findFirst()
                .orElseGet(() -> userSubs.stream()
                    .max(Comparator.comparing(UserSubscription::getCreatedAt))
                    .orElse(null)
                );

            String activePkg = "-";
            String purchaseDate = "-";
            String expiryDate = "-";

            if (selectedSub != null) {
                activePkg = selectedSub.getCampaignPackage().getName();
                purchaseDate = DATE_FORMATTER.format(selectedSub.getCreatedAt());
                expiryDate = DATE_FORMATTER.format(selectedSub.getExpiresAt());
            }

            content.add(new UserCreditSummaryResponse(
                uId,
                user.msisdn(),
                stats.getPurchased(),
                stats.getUsed(),
                remaining,
                user.status(),
                activePkg,
                purchaseDate,
                expiryDate
            ));
        }

        // If package filtering is requested, filter in memory.
        if (packageName != null && !packageName.trim().isEmpty()) {
            content = content.stream()
                .filter(u -> packageName.equalsIgnoreCase(u.packageName()))
                .collect(Collectors.toList());
        }

        var pageRequest = org.springframework.data.domain.PageRequest.of(page, size);
        long total = authUsersPage.totalElements();
        if (packageName != null && !packageName.trim().isEmpty()) {
            total = content.size();
        }

        PageResponse<UserCreditSummaryResponse> pageResult = PageResponse.from(new org.springframework.data.domain.PageImpl<>(
            content,
            pageRequest,
            total
        ));

        // Calculate statistics for all matching users (across all pages)
        List<Long> allMatchingUserIds = new ArrayList<>();
        try {
            allMatchingUserIds = authServiceClient.getUserIds(msisdn, status, startTime, endTime);
        } catch (Exception e) {
            // Fallback
        }

        List<Long> filteredUserIds = new ArrayList<>(allMatchingUserIds);
        if (packageName != null && !packageName.trim().isEmpty() && !allMatchingUserIds.isEmpty()) {
            List<UserSubscription> allSubs = userSubscriptionRepository.findByUserIds(allMatchingUserIds);
            Map<Long, List<UserSubscription>> allUserSubsMap = allSubs.stream()
                .collect(Collectors.groupingBy(UserSubscription::getUserId));
            
            filteredUserIds = allMatchingUserIds.stream().filter(uId -> {
                List<UserSubscription> subs = allUserSubsMap.getOrDefault(uId, Collections.emptyList());
                UserSubscription activeSub = subs.stream()
                    .filter(s -> s.getStatus() == UserSubscription.Status.ACTIVE)
                    .findFirst()
                    .orElseGet(() -> subs.stream()
                        .max(Comparator.comparing(UserSubscription::getCreatedAt))
                        .orElse(null));
                return activeSub != null && packageName.equalsIgnoreCase(activeSub.getCampaignPackage().getName());
            }).collect(Collectors.toList());
        }

        // Count active users among matching users
        long activeUsersCount = 0;
        if (status == null || status.isEmpty()) {
            try {
                PageResponse<UserResponse> activeUsersPage = authServiceClient.getUsers(msisdn, "ACTIVE", startTime, endTime, 0, 1);
                if (activeUsersPage != null) {
                    activeUsersCount = activeUsersPage.totalElements();
                }
            } catch (Exception e) {}
        } else if ("ACTIVE".equalsIgnoreCase(status)) {
            activeUsersCount = allMatchingUserIds.size();
        }

        long totalPurchased = 0;
        long totalUsed = 0;
        long totalRemaining = 0;

        if (!filteredUserIds.isEmpty()) {
            try {
                var walletSumResp = creditWalletClient.sumBalances(filteredUserIds);
                if (walletSumResp != null && walletSumResp.data() != null) {
                    totalRemaining = walletSumResp.data().longValue();
                }
            } catch (Exception e) {}

            try {
                var transSumResp = creditTransactionClient.sumStats(filteredUserIds);
                if (transSumResp != null && transSumResp.data() != null) {
                    totalPurchased = transSumResp.data().getPurchased();
                    totalUsed = transSumResp.data().getUsed();
                }
            } catch (Exception e) {}
        }

        UserCreditSummaryStats stats = new UserCreditSummaryStats(
            packageName != null && !packageName.trim().isEmpty() ? filteredUserIds.size() : authUsersPage.totalElements(),
            activeUsersCount,
            totalPurchased,
            totalUsed,
            totalRemaining
        );

        return new UserCreditSummaryPageWithStats(pageResult, stats);
    }
}
