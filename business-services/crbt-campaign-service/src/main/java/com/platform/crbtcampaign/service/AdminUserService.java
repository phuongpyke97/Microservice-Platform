package com.platform.crbtcampaign.service;

import com.platform.common.core.response.PageResponse;
import com.platform.crbtcampaign.client.AuthServiceClient;
import com.platform.crbtcampaign.client.CreditWalletClient;
import com.platform.crbtcampaign.client.CreditTransactionClient;
import com.platform.crbtcampaign.client.dto.UserResponse;
import com.platform.crbtcampaign.client.dto.UserCreditStats;
import com.platform.crbtcampaign.dto.response.UserCreditSummaryResponse;
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

    public PageResponse<UserCreditSummaryResponse> getUsersCreditSummary(
            String msisdn, String status, String packageName, int page, int size) {
        
        // 1. Fetch paginated users from Auth Service
        PageResponse<UserResponse> authUsersPage = authServiceClient.getUsers(msisdn, status, page, size);
        if (authUsersPage == null || authUsersPage.content() == null || authUsersPage.content().isEmpty()) {
            return PageResponse.from(new org.springframework.data.domain.PageImpl<>(
                Collections.emptyList(), 
                org.springframework.data.domain.PageRequest.of(page, size), 
                0
            ));
        }

        List<UserResponse> authUsers = authUsersPage.content();
        List<Long> userIds = authUsers.stream().map(UserResponse::id).collect(Collectors.toList());

        // 2. Fetch credit balances from Wallet Service in bulk
        Map<Long, Integer> balancesMap = new HashMap<>();
        try {
            var balancesResp = creditWalletClient.getBalances(userIds);
            if (balancesResp != null && balancesResp.data() != null) {
                balancesMap.putAll(balancesResp.data());
            }
        } catch (Exception e) {
            // Log or fallback silently
        }

        // 3. Fetch purchased/used credit stats from Credit Transaction Service in bulk
        Map<Long, UserCreditStats> statsMap = new HashMap<>();
        try {
            var statsResp = creditTransactionClient.getStats(userIds);
            if (statsResp != null && statsResp.data() != null) {
                statsMap.putAll(statsResp.data());
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

        return PageResponse.from(new org.springframework.data.domain.PageImpl<>(
            content,
            pageRequest,
            total
        ));
    }
}
