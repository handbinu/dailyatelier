package com.dailyatelier.dailyatelier.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PublicDemoAuctionMaintenanceScheduler {
    private final PublicDemoAuctionMaintenanceService service;
    @EventListener(ApplicationReadyEvent.class) public void onReady() { replenish("startup"); }
    @Scheduled(cron = "${dailyatelier.public-demo.maintenance-cron:0 5 0 * * *}") public void daily() { replenish("daily"); }

    private void replenish(String trigger) {
        try {
            service.replenish();
        } catch (RuntimeException exception) {
            log.error("공개 데모 경매 보충 실패: trigger={}", trigger, exception);
        }
    }
}
