package com.platform.crbtcampaign.repository;

import com.platform.crbtcampaign.entity.Campaign;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {
    List<Campaign> findByStatusAndStartAtBeforeAndEndAtAfter(Campaign.Status status, Instant now1, Instant now2);
}
