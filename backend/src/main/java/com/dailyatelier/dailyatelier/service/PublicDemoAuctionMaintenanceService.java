package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.*;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.ArtistRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PublicDemoAuctionMaintenanceService {
    private final ArtRepository artRepository;
    private final ArtistRepository artistRepository;
    private final Clock clock;
    @Value("${dailyatelier.public-demo.enabled:false}") private boolean enabled;
    @Value("${dailyatelier.public-demo.maintenance-enabled:false}") private boolean maintenanceEnabled;
    @Value("${dailyatelier.public-demo.artist-id:demo-artist-aria}") private String artistId;
    public void replenish() {
        if (!enabled || !maintenanceEnabled) {
            log.info("공개 데모 경매 보충 건너뜀: activeManagedCount=0, createdCount=0, skippedReason=DISABLED");
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        int createdCount = 0;
        createdCount += createIfMissing("public-demo:managed:bootstrap-1", now, now.plusHours(24)) ? 1 : 0;
        createdCount += createIfMissing("public-demo:managed:bootstrap-2", now, now.plusHours(48)) ? 1 : 0;
        createdCount += createIfMissing("public-demo:managed:bootstrap-3", now, now.plusHours(72)) ? 1 : 0;
        long activeManagedCount = artRepository.countByPublicDemoTypeAndArtStatusAndClosingTimeAfter(
                PublicDemoArtType.MANAGED, Art.STATUS_ACTIVE, now
        );
        String skippedReason = "TARGET_MET";
        if (activeManagedCount < 3) {
            boolean created = createIfMissing("public-demo:managed:daily:" + LocalDate.now(clock), now, now.plusHours(72));
            createdCount += created ? 1 : 0;
            skippedReason = created ? "NONE" : "DAILY_KEY_EXISTS_OR_RACE";
        }
        log.info(
                "공개 데모 경매 보충 완료: activeManagedCount={}, createdCount={}, skippedReason={}",
                activeManagedCount, createdCount, skippedReason
        );
    }
    private boolean createIfMissing(String key, LocalDateTime now, LocalDateTime closingTime) {
        if (artRepository.existsByPublicDemoCreationKey(key)) return false;
        Artist artist = artistRepository.findByUser_UserId(artistId).orElseThrow();
        Art art = new Art();
        art.setArtist(artist); art.setName("공개 데모 경매 " + key.substring(key.lastIndexOf(':') + 1));
        art.setDescript("공개 데모 입찰 체험용 작품입니다."); art.setMaterial("Demo archival print");
        art.setFormat(ArtFormat.PHYSICAL); art.setCategory(ArtCategory.PHOTOGRAPHY); art.setWIntro(artist.getArtistName());
        art.setStartPrice(10000); art.setCurrentPrice(10000); art.setMinimumBidIncrement(1000);
        art.setBidStartTime(now); art.setClosingTime(closingTime); art.setImgPath("/img/demo-art/art-demo-17-architecture.jpg");
        art.setArtStatus(Art.STATUS_ACTIVE); art.setCreatedAt(now); art.setPublicDemoType(PublicDemoArtType.MANAGED); art.setPublicDemoCreationKey(key);
        try {
            artRepository.saveAndFlush(art);
            return true;
        } catch (DataIntegrityViolationException ignored) {
            return false;
        }
    }
}
