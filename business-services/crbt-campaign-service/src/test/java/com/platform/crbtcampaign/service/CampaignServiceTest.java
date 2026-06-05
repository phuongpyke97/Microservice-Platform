package com.platform.crbtcampaign.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.common.core.exception.BaseException;
import com.platform.common.core.response.ApiResponse;
import com.platform.crbtcampaign.client.AuthServiceClient;
import com.platform.crbtcampaign.client.CreditWalletClient;
import com.platform.crbtcampaign.client.dto.UserCreditResponse;
import com.platform.crbtcampaign.client.dto.WalletAmountRequest;
import com.platform.crbtcampaign.client.dto.WalletResponse;
import com.platform.crbtcampaign.dto.request.CreateCampaignRequest;
import com.platform.crbtcampaign.dto.request.CreatePackageRequest;
import com.platform.crbtcampaign.dto.request.RenewSubscriptionRequest;
import com.platform.crbtcampaign.dto.response.CampaignResponse;
import com.platform.crbtcampaign.entity.Campaign;
import com.platform.crbtcampaign.entity.CampaignPackage;
import com.platform.crbtcampaign.entity.UserSubscription;
import com.platform.crbtcampaign.repository.CampaignPackageRepository;
import com.platform.crbtcampaign.repository.CampaignRepository;
import com.platform.crbtcampaign.repository.UserSubscriptionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CampaignServiceTest {

    @Mock CampaignRepository campaignRepository;
    @Mock CampaignPackageRepository packageRepository;
    @Mock UserSubscriptionRepository subscriptionRepository;
    @Mock CreditWalletClient creditWalletClient;
    @Mock AuthServiceClient authServiceClient;

    @InjectMocks CampaignService campaignService;

    @Test
    void createCampaign_shouldPersistAndReturn() {
        Instant start = Instant.now();
        Instant end = start.plus(30, ChronoUnit.DAYS);
        CreateCampaignRequest request = new CreateCampaignRequest(
            "Tet 2026", "Tet promo", start, end,
            List.of(new CreatePackageRequest("VIP", new BigDecimal("50000"), 100, 30))
        );

        when(campaignRepository.save(any(Campaign.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CampaignResponse response = campaignService.createCampaign(request);

        assertEquals("Tet 2026", response.name());
        assertEquals(1, response.packages().size());
    }

    @Test
    void subscribe_shouldThrowWhenPackageNotFound() {
        when(packageRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(BaseException.class, () -> campaignService.subscribe(1L, 99L, false));
    }

    @Test
    void subscribe_shouldThrowWhenAlreadySubscribedToSamePackage() {
        Campaign campaign = new Campaign("Tet", "desc", Campaign.Status.ACTIVE, Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS));
        CampaignPackage pkg = mock(CampaignPackage.class);
        when(pkg.getId()).thenReturn(1L);
        when(pkg.getCampaign()).thenReturn(campaign);

        UserSubscription activeSub = new UserSubscription(42L, pkg, UserSubscription.Status.ACTIVE, Instant.now().plus(5, ChronoUnit.DAYS));

        when(packageRepository.findById(1L)).thenReturn(Optional.of(pkg));
        when(subscriptionRepository.findByUserIdAndStatus(42L, UserSubscription.Status.ACTIVE)).thenReturn(List.of(activeSub));

        assertThrows(BaseException.class, () -> campaignService.subscribe(42L, 1L, false));
    }

    @Test
    void subscribe_shouldThrowWhenActiveSubscriptionExistsForDifferentPackage() {
        Campaign campaign = new Campaign("Tet", "desc", Campaign.Status.ACTIVE, Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS));
        CampaignPackage pkg = mock(CampaignPackage.class);
        when(pkg.getId()).thenReturn(1L);
        when(pkg.getCampaign()).thenReturn(campaign);

        CampaignPackage diffPkg = mock(CampaignPackage.class);
        when(diffPkg.getId()).thenReturn(2L);

        UserSubscription activeSub = new UserSubscription(42L, diffPkg, UserSubscription.Status.ACTIVE, Instant.now().plus(5, ChronoUnit.DAYS));

        when(packageRepository.findById(1L)).thenReturn(Optional.of(pkg));
        when(subscriptionRepository.findByUserIdAndStatus(42L, UserSubscription.Status.ACTIVE)).thenReturn(List.of(activeSub));

        assertThrows(BaseException.class, () -> campaignService.subscribe(42L, 1L, false));
    }

    @Test
    void subscribe_withConfirmChange_shouldSwitchPackageAndAccumulateCredits() {
        Instant now = Instant.now();
        Campaign campaign = new Campaign("Tet", "desc", Campaign.Status.ACTIVE, now.minus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS));
        CampaignPackage newPkg = mock(CampaignPackage.class);
        when(newPkg.getId()).thenReturn(1L);
        when(newPkg.getCampaign()).thenReturn(campaign);
        when(newPkg.getCreditAmount()).thenReturn(3);
        when(newPkg.getPrice()).thenReturn(new BigDecimal("10000"));
        when(newPkg.getValidityDays()).thenReturn(1);

        CampaignPackage oldPkg = mock(CampaignPackage.class);
        when(oldPkg.getId()).thenReturn(2L);
        UserSubscription activeSub = new UserSubscription(42L, oldPkg, UserSubscription.Status.ACTIVE, now.plus(5, ChronoUnit.HOURS));

        when(packageRepository.findById(1L)).thenReturn(Optional.of(newPkg));
        when(subscriptionRepository.findByUserIdAndStatus(42L, UserSubscription.Status.ACTIVE)).thenReturn(List.of(activeSub));
        when(subscriptionRepository.findByUserIdAndStatus(42L, UserSubscription.Status.CANCELLED)).thenReturn(List.of());

        campaignService.subscribe(42L, 1L, true);

        // Old subscription retired, new one created
        assertEquals(UserSubscription.Status.EXPIRED, activeSub.getStatus());
        verify(subscriptionRepository, times(2)).save(any(UserSubscription.class));
        // Accumulate: grant new quota, NO reset (no deduct) — leftover credits survive the switch
        verify(creditWalletClient).add(eq(42L), any(WalletAmountRequest.class));
        verify(creditWalletClient, never()).deduct(eq(42L), any(WalletAmountRequest.class));
    }

    @Test
    void subscribe_shouldDeactivateCancelledSubscriptionsAndAddCredits() {
        Instant now = Instant.now();
        Campaign campaign = new Campaign("Tet", "desc", Campaign.Status.ACTIVE, now.minus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS));
        CampaignPackage pkg = mock(CampaignPackage.class);
        when(pkg.getCampaign()).thenReturn(campaign);
        when(pkg.getCreditAmount()).thenReturn(100);
        when(pkg.getPrice()).thenReturn(new BigDecimal("50000"));
        when(pkg.getValidityDays()).thenReturn(30);

        UserSubscription cancelledSub = new UserSubscription(42L, pkg, UserSubscription.Status.CANCELLED, now.plus(5, ChronoUnit.DAYS));

        when(packageRepository.findById(1L)).thenReturn(Optional.of(pkg));
        when(subscriptionRepository.findByUserIdAndStatus(42L, UserSubscription.Status.ACTIVE)).thenReturn(List.of());
        when(subscriptionRepository.findByUserIdAndStatus(42L, UserSubscription.Status.CANCELLED)).thenReturn(List.of(cancelledSub));

        campaignService.subscribe(42L, 1L, false);

        assertEquals(UserSubscription.Status.EXPIRED, cancelledSub.getStatus());
        verify(subscriptionRepository, times(2)).save(any(UserSubscription.class));
        verify(creditWalletClient).add(eq(42L), any(WalletAmountRequest.class));
    }

    @Test
    void unsubscribe_shouldSetStatusToCancelled() {
        CampaignPackage pkg = mock(CampaignPackage.class);
        UserSubscription sub = new UserSubscription(42L, pkg, UserSubscription.Status.ACTIVE, Instant.now().plus(5, ChronoUnit.DAYS));

        when(subscriptionRepository.findByUserIdAndStatus(42L, UserSubscription.Status.ACTIVE)).thenReturn(List.of(sub));

        campaignService.unsubscribe(42L);

        assertEquals(UserSubscription.Status.CANCELLED, sub.getStatus());
        verify(subscriptionRepository).save(sub);
    }

    @Test
    void renewSubscriptionInternal_shouldResetBalanceAndAddCredits() {
        Instant now = Instant.now();
        Campaign campaign = new Campaign("Tet", "desc", Campaign.Status.ACTIVE, now.minus(5, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS));
        CampaignPackage pkg = new CampaignPackage(campaign, "VIP", new BigDecimal("50000"), 100, 30);
        UserSubscription sub = new UserSubscription(42L, pkg, UserSubscription.Status.ACTIVE, now.plus(1, ChronoUnit.DAYS));

        RenewSubscriptionRequest request = new RenewSubscriptionRequest("0912345678", "VIP", now.plus(31, ChronoUnit.DAYS));
        UserCreditResponse userCredit = new UserCreditResponse(42L, "0912345678");

        when(authServiceClient.getUserCredit("0912345678")).thenReturn(userCredit);
        when(subscriptionRepository.findByUserIdAndStatus(42L, UserSubscription.Status.ACTIVE)).thenReturn(List.of(sub));
        
        ApiResponse<WalletResponse> balanceResp = ApiResponse.success(new WalletResponse(42L, 15));
        when(creditWalletClient.getBalance(42L)).thenReturn(balanceResp);

        campaignService.renewSubscriptionInternal(request);

        verify(creditWalletClient).deduct(eq(42L), any(WalletAmountRequest.class));
        verify(creditWalletClient).add(eq(42L), any(WalletAmountRequest.class));
        verify(subscriptionRepository).save(sub);
        assertEquals(request.expiresAt(), sub.getExpiresAt());
    }

    @Test
    void renewSubscriptions_shouldRenewExpiringSubscriptions() {
        Instant now = Instant.now();
        Campaign campaign = new Campaign("Tet", "desc", Campaign.Status.ACTIVE, now.minus(5, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS));
        CampaignPackage pkg = new CampaignPackage(campaign, "VIP", new BigDecimal("50000"), 100, 30);
        UserSubscription sub = new UserSubscription(42L, pkg, UserSubscription.Status.ACTIVE, now.minus(1, ChronoUnit.HOURS));

        when(subscriptionRepository.findAllByStatusAndAutoRenewAndExpiresAtBefore(any(), eq(true), any())).thenReturn(List.of(sub));
        ApiResponse<WalletResponse> balanceResp = ApiResponse.success(new WalletResponse(42L, 5));
        when(creditWalletClient.getBalance(42L)).thenReturn(balanceResp);

        int renewed = campaignService.renewSubscriptions();

        assertEquals(1, renewed);
        verify(creditWalletClient).deduct(eq(42L), any(WalletAmountRequest.class));
        verify(creditWalletClient).add(eq(42L), any(WalletAmountRequest.class));
        verify(subscriptionRepository).save(sub);
    }

    @Test
    void cleanupExpiredTokens_shouldResetBalanceAndMarkExpired() {
        Instant now = Instant.now();
        CampaignPackage pkg = mock(CampaignPackage.class);
        UserSubscription sub = new UserSubscription(42L, pkg, UserSubscription.Status.CANCELLED, now.minus(1, ChronoUnit.HOURS));

        when(subscriptionRepository.findByStatusInAndExpiresAtBefore(any(), any())).thenReturn(List.of(sub));
        ApiResponse<WalletResponse> balanceResp = ApiResponse.success(new WalletResponse(42L, 10));
        when(creditWalletClient.getBalance(42L)).thenReturn(balanceResp);

        int count = campaignService.cleanupExpiredTokens();

        assertEquals(1, count);
        verify(creditWalletClient).deduct(eq(42L), any(WalletAmountRequest.class));
        assertEquals(UserSubscription.Status.EXPIRED, sub.getStatus());
        verify(subscriptionRepository).save(sub);
    }
}
