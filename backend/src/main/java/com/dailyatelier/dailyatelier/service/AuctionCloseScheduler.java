package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class AuctionCloseScheduler {

    private final AuctionCloseService auctionCloseService;
    private final ArtRepository artRepository;
    private final Clock clock;
    private final int batchSize;

    public AuctionCloseScheduler(
            AuctionCloseService auctionCloseService,
            ArtRepository artRepository,
            Clock clock,
            @Value("${auction.close.batch-size:100}") int batchSize) {
        this.auctionCloseService = auctionCloseService;
        this.artRepository = artRepository;
        this.clock = clock;
        this.batchSize = Math.max(batchSize, 1);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void closeExpiredAuctionsOnStartup() {
        closeExpiredAuctions();
    }

    @Scheduled(
            fixedRateString = "${auction.close.interval-ms:10000}",
            initialDelayString = "${auction.close.interval-ms:10000}"
    )
    public void closeExpiredAuctions() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> artIds = artRepository.findExpiredActiveArtIds(
                Art.STATUS_ACTIVE,
                now,
                PageRequest.of(0, batchSize)
        );

        int soldCount = 0;
        int unsoldCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        for (Long artId : artIds) {
            try {
                AuctionCloseResult result = auctionCloseService.closeAuction(artId);
                switch (result) {
                    case SOLD -> soldCount++;
                    case UNSOLD -> unsoldCount++;
                    case NOT_DUE, ALREADY_CLOSED -> skippedCount++;
                }
            } catch (RuntimeException exception) {
                failedCount++;
                log.error("경매 자동 마감 실패: artId={}", artId, exception);
            }
        }

        if (!artIds.isEmpty() || failedCount > 0) {
            log.info(
                    "경매 자동 마감 배치 완료: targetCount={}, soldCount={}, "
                            + "unsoldCount={}, skippedCount={}, failedCount={}",
                    artIds.size(),
                    soldCount,
                    unsoldCount,
                    skippedCount,
                    failedCount
            );
        }
    }
}
