package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.PublicDemoArtType;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.ArtistRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicDemoAuctionMaintenanceServiceTest {

    @Mock
    private ArtRepository artRepository;
    @Mock
    private ArtistRepository artistRepository;

    @Test
    void createsThreeBootstrapAuctionsWithStaggeredClosingTimes() {
        PublicDemoAuctionMaintenanceService service = service();
        when(artistRepository.findByUser_UserId("demo-artist-aria"))
                .thenReturn(Optional.of(new Artist()));
        when(artRepository.existsByPublicDemoCreationKey(anyString())).thenReturn(false);
        when(artRepository.countByPublicDemoTypeAndArtStatusAndClosingTimeAfter(
                PublicDemoArtType.MANAGED, Art.STATUS_ACTIVE,
                java.time.LocalDateTime.of(2026, 11, 18, 0, 0))).thenReturn(3L);

        service.replenish();

        ArgumentCaptor<Art> saved = ArgumentCaptor.forClass(Art.class);
        verify(artRepository, times(3)).saveAndFlush(saved.capture());
        List<Art> auctions = saved.getAllValues();
        assertThat(auctions).extracting(Art::getPublicDemoCreationKey).containsExactly(
                "public-demo:managed:bootstrap-1",
                "public-demo:managed:bootstrap-2",
                "public-demo:managed:bootstrap-3");
        assertThat(auctions).extracting(Art::getClosingTime).containsExactly(
                java.time.LocalDateTime.of(2026, 11, 19, 0, 0),
                java.time.LocalDateTime.of(2026, 11, 20, 0, 0),
                java.time.LocalDateTime.of(2026, 11, 21, 0, 0));
    }

    @Test
    void absorbsUniqueKeyRaceWithoutCreatingDuplicateState() {
        PublicDemoAuctionMaintenanceService service = service();
        when(artistRepository.findByUser_UserId("demo-artist-aria"))
                .thenReturn(Optional.of(new Artist()));
        when(artRepository.existsByPublicDemoCreationKey(anyString())).thenReturn(false);
        when(artRepository.saveAndFlush(any(Art.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(artRepository.countByPublicDemoTypeAndArtStatusAndClosingTimeAfter(
                any(), any(), any())).thenReturn(3L);

        assertThatCode(service::replenish).doesNotThrowAnyException();
    }

    @Test
    void addsAtMostOneDailyAuctionWhenManagedInventoryIsLow() {
        PublicDemoAuctionMaintenanceService service = service();
        when(artistRepository.findByUser_UserId("demo-artist-aria"))
                .thenReturn(Optional.of(new Artist()));
        when(artRepository.existsByPublicDemoCreationKey("public-demo:managed:bootstrap-1"))
                .thenReturn(true);
        when(artRepository.existsByPublicDemoCreationKey("public-demo:managed:bootstrap-2"))
                .thenReturn(true);
        when(artRepository.existsByPublicDemoCreationKey("public-demo:managed:bootstrap-3"))
                .thenReturn(true);
        when(artRepository.existsByPublicDemoCreationKey("public-demo:managed:daily:2026-11-18"))
                .thenReturn(false);
        when(artRepository.countByPublicDemoTypeAndArtStatusAndClosingTimeAfter(
                any(), any(), any())).thenReturn(2L);

        service.replenish();

        ArgumentCaptor<Art> saved = ArgumentCaptor.forClass(Art.class);
        verify(artRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPublicDemoCreationKey())
                .isEqualTo("public-demo:managed:daily:2026-11-18");
        assertThat(saved.getValue().getClosingTime())
                .isEqualTo(java.time.LocalDateTime.of(2026, 11, 21, 0, 0));
    }

    private PublicDemoAuctionMaintenanceService service() {
        PublicDemoAuctionMaintenanceService service = new PublicDemoAuctionMaintenanceService(
                artRepository,
                artistRepository,
                Clock.fixed(Instant.parse("2026-11-18T00:00:00Z"), ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "maintenanceEnabled", true);
        ReflectionTestUtils.setField(service, "artistId", "demo-artist-aria");
        return service;
    }
}
