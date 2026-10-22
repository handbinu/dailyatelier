package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class AuctionCloseSchedulerTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-27T09:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    @Mock
    private AuctionCloseService auctionCloseService;

    @Mock
    private ArtRepository artRepository;

    private AuctionCloseScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AuctionCloseScheduler(
                auctionCloseService,
                artRepository,
                CLOCK,
                5
        );
    }

    @Test
    void processesEachArtAndLogsBatchSummaryWithoutStoppingOnFailure(
            CapturedOutput output) {
        when(artRepository.findExpiredActiveArtIds(
                org.mockito.ArgumentMatchers.eq(Art.STATUS_ACTIVE),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(List.of(1L, 2L, 3L, 4L, 5L));
        when(auctionCloseService.closeAuction(1L)).thenReturn(AuctionCloseResult.SOLD);
        when(auctionCloseService.closeAuction(2L)).thenReturn(AuctionCloseResult.UNSOLD);
        when(auctionCloseService.closeAuction(3L)).thenReturn(AuctionCloseResult.NOT_DUE);
        when(auctionCloseService.closeAuction(4L))
                .thenThrow(new IllegalStateException("forced failure"));
        when(auctionCloseService.closeAuction(5L))
                .thenReturn(AuctionCloseResult.ALREADY_CLOSED);

        scheduler.closeExpiredAuctions();

        verify(auctionCloseService).closeAuction(5L);
        assertThat(output)
                .contains("경매 자동 마감 실패: artId=4")
                .contains("targetCount=5")
                .contains("soldCount=1")
                .contains("unsoldCount=1")
                .contains("skippedCount=2")
                .contains("failedCount=1");
    }

    @Test
    void startupCatchUpUsesCurrentTimeAndConfiguredBatchSize() {
        ArgumentCaptor<LocalDateTime> nowCaptor =
                ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);
        when(artRepository.findExpiredActiveArtIds(
                org.mockito.ArgumentMatchers.eq(Art.STATUS_ACTIVE),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(List.of());

        scheduler.closeExpiredAuctionsOnStartup();

        verify(artRepository).findExpiredActiveArtIds(
                org.mockito.ArgumentMatchers.eq(Art.STATUS_ACTIVE),
                nowCaptor.capture(),
                pageableCaptor.capture()
        );
        assertThat(nowCaptor.getValue())
                .isEqualTo(LocalDateTime.of(2026, 7, 27, 18, 0));
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void failedArtIsRetriedOnNextCycle() {
        when(artRepository.findExpiredActiveArtIds(
                org.mockito.ArgumentMatchers.eq(Art.STATUS_ACTIVE),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(List.of(7L));
        when(auctionCloseService.closeAuction(7L))
                .thenThrow(new IllegalStateException("temporary failure"));

        scheduler.closeExpiredAuctions();
        scheduler.closeExpiredAuctions();

        verify(auctionCloseService, times(2)).closeAuction(7L);
    }
}
