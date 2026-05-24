package com.platform.crbtcampaign.scheduler;

import com.platform.crbtcampaign.service.CampaignService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CampaignScheduler {

    private static final Logger log = LoggerFactory.getLogger(CampaignScheduler.class);

    private final CampaignService campaignService;

    public CampaignScheduler(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    /**
     * Auto-renew subscriptions daily at 00:00 (T8.7)
     * Cron: second minute hour day month weekday
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void autoRenewSubscriptions() {
        log.info("Starting auto-renew job at 00:00");
        try {
            int renewed = campaignService.renewSubscriptions();
            log.info("Auto-renew completed: {} subscriptions renewed", renewed);
        } catch (Exception e) {
            log.error("Auto-renew job failed", e);
        }
    }
}
