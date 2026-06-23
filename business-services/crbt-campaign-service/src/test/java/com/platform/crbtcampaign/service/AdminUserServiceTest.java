package com.platform.crbtcampaign.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.platform.common.core.response.ApiResponse;
import com.platform.common.core.response.PageResponse;
import com.platform.crbtcampaign.client.AuthServiceClient;
import com.platform.crbtcampaign.client.CreditWalletClient;
import com.platform.crbtcampaign.client.CreditTransactionClient;
import com.platform.crbtcampaign.client.dto.UserResponse;
import com.platform.crbtcampaign.client.dto.UserCreditStats;
import com.platform.crbtcampaign.dto.response.UserCreditSummaryResponse;
import com.platform.crbtcampaign.dto.response.UserCreditSummaryPageWithStats;
import com.platform.crbtcampaign.dto.response.UserCreditSummaryStats;
import com.platform.crbtcampaign.entity.CampaignPackage;
import com.platform.crbtcampaign.entity.UserSubscription;
import com.platform.crbtcampaign.repository.UserSubscriptionRepository;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private AuthServiceClient authServiceClient;
    @Mock
    private CreditWalletClient creditWalletClient;
    @Mock
    private CreditTransactionClient creditTransactionClient;
    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(
            authServiceClient,
            creditWalletClient,
            creditTransactionClient,
            userSubscriptionRepository
        );
    }

    @Test
    void getUsersCreditSummary_shouldAggregateCorrectly() {
        // Mock Auth Users
        UserResponse user1 = new UserResponse(1L, "84987000101", "user1@test.com", "active", 1000L);
        UserResponse user2 = new UserResponse(2L, "84987000102", "user2@test.com", "deactive", 2000L);
        List<UserResponse> usersList = List.of(user1, user2);
        
        PageResponse<UserResponse> authUsersPage = PageResponse.from(new PageImpl<>(
            usersList, PageRequest.of(0, 20), 2
        ));
        when(authServiceClient.getUsers("84987", null, null, null, 0, 20)).thenReturn(authUsersPage);

        // Mock Wallet Balances
        Map<Long, Integer> balances = Map.of(1L, 10, 2L, 5);
        when(creditWalletClient.getBalances(List.of(1L, 2L)))
            .thenReturn(new ApiResponse<>(true, "success", balances, System.currentTimeMillis()));

        // Mock Transaction Stats
        Map<Long, UserCreditStats> stats = Map.of(
            1L, new UserCreditStats(15L, 5L),
            2L, new UserCreditStats(5L, 0L)
        );
        when(creditTransactionClient.getStats(List.of(1L, 2L)))
            .thenReturn(new ApiResponse<>(true, "success", stats, System.currentTimeMillis()));

        // Mock Aggregation Stats Client Calls
        when(authServiceClient.getUserIds("84987", null, null, null)).thenReturn(List.of(1L, 2L));
        when(creditWalletClient.sumBalances(List.of(1L, 2L)))
            .thenReturn(new ApiResponse<>(true, "success", 15, System.currentTimeMillis()));
        when(creditTransactionClient.sumStats(List.of(1L, 2L)))
            .thenReturn(new ApiResponse<>(true, "success", new UserCreditStats(20L, 5L), System.currentTimeMillis()));

        // Mock Subscriptions
        CampaignPackage pkg = mock(CampaignPackage.class);
        when(pkg.getName()).thenReturn("Weekly 5K");
        UserSubscription sub = mock(UserSubscription.class);
        when(sub.getUserId()).thenReturn(1L);
        when(sub.getStatus()).thenReturn(UserSubscription.Status.ACTIVE);
        when(sub.getCampaignPackage()).thenReturn(pkg);
        when(sub.getCreatedAt()).thenReturn(Instant.now());
        when(sub.getExpiresAt()).thenReturn(Instant.now().plusSeconds(3600 * 24 * 7));

        when(userSubscriptionRepository.findByUserIds(List.of(1L, 2L))).thenReturn(List.of(sub));

        // Execute
        UserCreditSummaryPageWithStats result = adminUserService.getUsersCreditSummary(
            "84987", null, null, null, null, 0, 20
        );

        // Verify
        assertNotNull(result);
        assertNotNull(result.page());
        assertEquals(2, result.page().content().size());

        assertNotNull(result.stats());
        assertEquals(2, result.stats().totalUsers());
        assertEquals(20, result.stats().totalPurchased());
        assertEquals(5, result.stats().totalUsed());
        assertEquals(15, result.stats().totalRemaining());

        UserCreditSummaryResponse res1 = result.page().content().stream()
            .filter(r -> r.userId().equals(1L))
            .findFirst().orElse(null);
        assertNotNull(res1);
        assertEquals("84987000101", res1.msisdn());
        assertEquals(15L, res1.purchased());
        assertEquals(5L, res1.used());
        assertEquals(10, res1.remaining());
        assertEquals("active", res1.status());
        assertEquals("Weekly 5K", res1.packageName());

        UserCreditSummaryResponse res2 = result.page().content().stream()
            .filter(r -> r.userId().equals(2L))
            .findFirst().orElse(null);
        assertNotNull(res2);
        assertEquals("deactive", res2.status());
        assertEquals("-", res2.packageName());
    }
}
