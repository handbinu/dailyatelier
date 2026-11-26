package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.DueBatchCandidate;
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
        when(artRepository.findExpiredActiveArtBatch(
                org.mockito.ArgumentMatchers.eq(Art.STATUS_ACTIVE),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(List.of(candidate(1L), candidate(2L), candidate(3L), candidate(4L), candidate(5L)));
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
        when(artRepository.findExpiredActiveArtBatch(
                org.mockito.ArgumentMatchers.eq(Art.STATUS_ACTIVE),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(List.of());

        scheduler.closeExpiredAuctionsOnStartup();

        verify(artRepository).findExpiredActiveArtBatch(
                org.mockito.ArgumentMatchers.eq(Art.STATUS_ACTIVE),
                nowCaptor.capture(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                pageableCaptor.capture()
        );
        assertThat(nowCaptor.getValue())
                .isEqualTo(LocalDateTime.of(2026, 7, 27, 18, 0));
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void failedArtIsRetriedOnNextCycle() {
        when(artRepository.findExpiredActiveArtBatch(
                org.mockito.ArgumentMatchers.eq(Art.STATUS_ACTIVE),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(List.of(candidate(7L)));
        when(auctionCloseService.closeAuction(7L))
                .thenThrow(new IllegalStateException("temporary failure"));

        scheduler.closeExpiredAuctions();
        scheduler.closeExpiredAuctions();

        verify(auctionCloseService, times(2)).closeAuction(7L);
    }

    @Test
    void advancesPastFailedFullBatchAndRestartsAfterTail() {
        LocalDateTime cutoff = LocalDateTime.now(CLOCK);
        var head = candidate(7L);
        var tail = candidate(8L);
        var limit = org.springframework.data.domain.PageRequest.of(0, 1);
        var onePerRun = new AuctionCloseScheduler(auctionCloseService, artRepository, CLOCK, 1);
        when(artRepository.findExpiredActiveArtBatch(Art.STATUS_ACTIVE, cutoff, null, null, limit))
                .thenReturn(List.of(head));
        when(artRepository.findExpiredActiveArtBatch(Art.STATUS_ACTIVE, cutoff, cutoff, 7L, limit))
                .thenReturn(List.of(tail));
        when(artRepository.findExpiredActiveArtBatch(Art.STATUS_ACTIVE, cutoff, cutoff, 8L, limit))
                .thenReturn(List.of());
        when(auctionCloseService.closeAuction(7L))
                .thenThrow(new IllegalStateException("fixture failure"))
                .thenReturn(AuctionCloseResult.UNSOLD);
        when(auctionCloseService.closeAuction(8L)).thenReturn(AuctionCloseResult.UNSOLD);

        onePerRun.closeExpiredAuctions();
        onePerRun.closeExpiredAuctions();
        onePerRun.closeExpiredAuctions();
        onePerRun.closeExpiredAuctions();

        var sequence = org.mockito.Mockito.inOrder(auctionCloseService);
        sequence.verify(auctionCloseService).closeAuction(7L);
        sequence.verify(auctionCloseService).closeAuction(8L);
        sequence.verify(auctionCloseService).closeAuction(7L);
        sequence.verifyNoMoreInteractions();
    }

    private static DueBatchCandidate candidate(long id) {
        return new TestCandidate(id, LocalDateTime.now(CLOCK));
    }

    private record TestCandidate(Long id, LocalDateTime deadline) implements DueBatchCandidate {
        @Override public Long getId() { return id; }
        @Override public LocalDateTime getDeadline() { return deadline; }
    }
}
