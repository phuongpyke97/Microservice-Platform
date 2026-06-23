package com.platform.crbtcampaign.repository;

import com.platform.crbtcampaign.entity.UserSubscription;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {
    List<UserSubscription> findByUserIdAndStatus(Long userId, UserSubscription.Status status);
    List<UserSubscription> findAllByStatusAndAutoRenewAndExpiresAtBefore(
        UserSubscription.Status status, boolean autoRenew, Instant expiresAt);
    List<UserSubscription> findByStatusInAndExpiresAtBefore(
        List<UserSubscription.Status> statuses, Instant expiresAt);

    @org.springframework.data.jpa.repository.Query("SELECT s FROM UserSubscription s JOIN FETCH s.campaignPackage WHERE s.userId IN :userIds")
    List<UserSubscription> findByUserIds(@org.springframework.data.repository.query.Param("userIds") List<Long> userIds);
}

