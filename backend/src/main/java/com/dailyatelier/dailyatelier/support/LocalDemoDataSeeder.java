package com.dailyatelier.dailyatelier.support;

import com.dailyatelier.dailyatelier.dto.BidCreateRequestDto;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.ArtCategory;
import com.dailyatelier.dailyatelier.entity.ArtFormat;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.PointAccount;
import com.dailyatelier.dailyatelier.entity.PointReferenceType;
import com.dailyatelier.dailyatelier.entity.PointTransaction;
import com.dailyatelier.dailyatelier.entity.PointTransactionType;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.ArtistRepository;
import com.dailyatelier.dailyatelier.repository.BidRepository;
import com.dailyatelier.dailyatelier.repository.OrderRepository;
import com.dailyatelier.dailyatelier.repository.PointAccountRepository;
import com.dailyatelier.dailyatelier.repository.PointTransactionRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import com.dailyatelier.dailyatelier.service.AuctionCloseService;
import com.dailyatelier.dailyatelier.service.BidService;
import com.dailyatelier.dailyatelier.service.PointAccountService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Profile("local-demo")
@ConditionalOnProperty(prefix = "dailyatelier.local-demo", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class LocalDemoDataSeeder {
    private static final String PASSWORD = "demo-password";
    private static final long BUYER_POINTS = 5_000_000L;

    private final UserRepository userRepository;
    private final ArtistRepository artistRepository;
    private final ArtRepository artRepository;
    private final BidRepository bidRepository;
    private final OrderRepository orderRepository;
    private final PointAccountService pointAccountService;
    private final PointAccountRepository pointAccountRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final PasswordEncoder passwordEncoder;
    private final BidService bidService;
    private final AuctionCloseService auctionCloseService;
    private final Clock clock;

    @Transactional
    public void seed() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Artist> artists = List.of(
                artist("demo-artist-aria", "아리아", "aria@demo.local", "아리아 김", "색과 빛의 리듬을 회화로 기록합니다."),
                artist("demo-artist-min", "민서", "min@demo.local", "민서 윤", "일상의 오브제를 낯선 균형으로 재구성합니다."),
                artist("demo-artist-jun", "준호", "jun@demo.local", "준호 박", "공간과 재료가 만나는 순간을 탐구합니다."),
                artist("demo-artist-soo", "수아", "soo@demo.local", "수아 이", "사진과 디지털 매체로 감각의 층을 만듭니다."),
                artist("demo-artist-han", "하늘", "han@demo.local", "하늘 한", "느린 관찰에서 출발한 조형 언어를 작업합니다.")
        );
        User buyerOne = user("demo-buyer-one", "도윤", "buyer1@demo.local", 0);
        User buyerTwo = user("demo-buyer-two", "유진", "buyer2@demo.local", 0);
        User buyerThree = user("demo-buyer-three", "서연", "buyer3@demo.local", 0);
        fund(buyerOne, now);
        fund(buyerTwo, now);
        fund(buyerThree, now);

        List<DemoArt> arts = List.of(
                art(artists.get(0), "코랄 블루의 오후", "art-demo-01-coral-blue.jpg", ArtFormat.PHYSICAL, ArtCategory.ACRYLIC_PAINTING, 180000, 10000, 72, DemoArtRole.ONGOING),
                art(artists.get(0), "파스텔의 잔상", "art-demo-02-pastel-abstract.jpg", ArtFormat.PHYSICAL, ArtCategory.OIL_PAINTING, 240000, 10000, 36, DemoArtRole.ONGOING),
                art(artists.get(0), "컬러 필드, 봄", "art-demo-03-acrylic-color.jpg", ArtFormat.PHYSICAL, ArtCategory.ACRYLIC_PAINTING, 320000, 20000, 8, DemoArtRole.ONGOING),
                art(artists.get(2), "정적의 구조", "art-demo-04-geometric.jpg", ArtFormat.PHYSICAL, ArtCategory.MIXED_MEDIA, 280000, 10000, 120, DemoArtRole.ONGOING),
                art(artists.get(0), "미니멀 브리즈", "art-demo-05-minimal.jpg", ArtFormat.PHYSICAL, ArtCategory.OIL_PAINTING, 210000, 10000, 20, DemoArtRole.ONGOING),
                art(artists.get(0), "오렌지 코발트", "art-demo-06-orange-blue.jpg", ArtFormat.PHYSICAL, ArtCategory.ACRYLIC_PAINTING, 430000, 10000, 4, DemoArtRole.ONGOING),
                art(artists.get(1), "소프트 테이블", "art-demo-07-pastel-stilllife.jpg", ArtFormat.PHYSICAL, ArtCategory.PHOTOGRAPHY, 160000, 10000, 15, DemoArtRole.ONGOING),
                art(artists.get(1), "세라믹 리듬", "art-demo-08-ceramic.jpg", ArtFormat.PHYSICAL, ArtCategory.CRAFT, 260000, 10000, 96, DemoArtRole.ONGOING),
                art(artists.get(2), "파스텔 모노리스", "art-demo-09-pastel-sculpture.jpg", ArtFormat.PHYSICAL, ArtCategory.SCULPTURE, 380000, 20000, -48, DemoArtRole.SOLD),
                art(artists.get(2), "고요한 조각", "art-demo-10-modern-sculpture.jpg", ArtFormat.PHYSICAL, ArtCategory.SCULPTURE, 460000, 20000, -72, DemoArtRole.SOLD),
                art(artists.get(2), "프리즘의 방", "art-demo-11-glass.jpg", ArtFormat.PHYSICAL, ArtCategory.CRAFT, 340000, 10000, -96, DemoArtRole.SOLD),
                art(artists.get(2), "투명한 오후", "art-demo-12-glass-shadow.jpg", ArtFormat.PHYSICAL, ArtCategory.PHOTOGRAPHY, 230000, 10000, -120, DemoArtRole.UNSOLD),
                art(artists.get(3), "블룸 에디토리얼", "art-demo-13-flower-editorial.jpg", ArtFormat.PHYSICAL, ArtCategory.PHOTOGRAPHY, 190000, 10000, -144, DemoArtRole.UNSOLD),
                art(artists.get(3), "꽃의 균형", "art-demo-14-flower-stilllife.jpg", ArtFormat.PHYSICAL, ArtCategory.PHOTOGRAPHY, 270000, 10000, -168, DemoArtRole.UPCOMING),
                art(artists.get(4), "페이퍼 가든", "art-demo-15-paper-art.jpg", ArtFormat.PHYSICAL, ArtCategory.MIXED_MEDIA, 220000, 10000, -192, DemoArtRole.UPCOMING),
                art(artists.get(1), "기하학적 오후", "art-demo-16-geometric-stilllife.jpg", ArtFormat.PHYSICAL, ArtCategory.PHOTOGRAPHY, 250000, 10000, -216, DemoArtRole.UPCOMING),
                art(artists.get(3), "색의 파사드", "art-demo-17-architecture.jpg", ArtFormat.DIGITAL, ArtCategory.DIGITAL_ART, 300000, 10000, -240, DemoArtRole.UPCOMING),
                art(artists.get(3), "네온 스펙트럼", "art-demo-18-neon.jpg", ArtFormat.DIGITAL, ArtCategory.DIGITAL_ART, 410000, 20000, -264, DemoArtRole.SOLD),
                art(artists.get(4), "텍스타일 파동", "art-demo-19-textile.jpg", ArtFormat.PHYSICAL, ArtCategory.MIXED_MEDIA, 290000, 10000, -288, DemoArtRole.UPCOMING),
                art(artists.get(4), "세이지 브레스", "art-demo-20-sage.jpg", ArtFormat.PHYSICAL, ArtCategory.OIL_PAINTING, 360000, 20000, -312, DemoArtRole.UPCOMING)
        );
        for (DemoArt spec : arts) {
            Art art = artRepository.findByArtistUserUserIdAndName(
                    spec.artist().getUser().getUserId(), spec.name()).orElseGet(() -> create(spec, now));
            refreshSafeAuctionWindow(art, spec, now);
            if (spec.role() == DemoArtRole.SOLD && art.getArtStatus() == Art.STATUS_ACTIVE) {
                closeSoldArt(art, spec.startPrice() + spec.increment(), buyerFor(art.getName(), buyerOne, buyerTwo, buyerThree));
            }
        }
        log.info("Local demo seed is ready: {} demo artworks", arts.size());
    }

    private Artist artist(String id, String nickname, String email, String artistName, String intro) {
        User user = user(id, nickname, email, 1);
        return artistRepository.findByUser(user).orElseGet(() -> {
            Artist artist = new Artist();
            artist.setUser(user);
            artist.setArtistName(artistName);
            artist.setArtistIntro(intro);
            artist.setHomepage("https://dailyatelier.local/" + id);
            return artistRepository.save(artist);
        });
    }

    private User user(String id, String nickname, String email, int status) {
        User existing = userRepository.findByUserId(id);
        if (existing != null) return existing;
        User user = new User();
        user.setUserId(id);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setName(nickname + " 데모");
        user.setNickname(nickname);
        user.setPhoneNumber("010-0000-" + String.format("%04d", Math.abs(id.hashCode()) % 10000));
        user.setEmail(email);
        user.setJoinDate(LocalDateTime.now(clock));
        user.setUserStatus(status);
        return userRepository.save(user);
    }

    private void fund(User user, LocalDateTime now) {
        PointAccount account = pointAccountService.initializeAccount(user.getUserId());
        String key = "local-demo:fund:" + user.getUserId();
        if (pointTransactionRepository.existsByIdempotencyKey(key)) return;
        account.credit(BUYER_POINTS, now);
        pointAccountRepository.save(account);
        pointTransactionRepository.save(PointTransaction.record(user.getUserId(), PointTransactionType.DEMO_CHARGE,
                BUYER_POINTS, BUYER_POINTS, 0, account.getAvailableBalance(), account.getHeldBalance(),
                PointReferenceType.USER, user.getUserId(), key, null, "LOCAL_DEMO_SEED", "로컬 demo 입찰 포인트", now));
    }

    private Art create(DemoArt spec, LocalDateTime now) {
        Art art = new Art();
        art.setArtist(spec.artist());
        art.setName(spec.name());
        art.setDescript("로컬 demo 화면 검증을 위한 " + spec.name() + " 작품입니다.");
        art.setMaterial(spec.format() == ArtFormat.DIGITAL ? "Digital archival file" : "Mixed media on archival support");
        art.setFormat(spec.format());
        art.setCategory(spec.category());
        art.setWIntro(spec.artist().getArtistName() + "의 demo 컬렉션");
        art.setStartPrice(spec.startPrice());
        art.setCurrentPrice(spec.startPrice());
        art.setMinimumBidIncrement(spec.increment());
        applyAuctionWindow(art, spec, now);
        art.setImgPath("/img/demo-art/" + spec.image());
        if (spec.role() == DemoArtRole.UNSOLD) {
            art.setArtStatus(Art.STATUS_UNSOLD);
            art.setClosedAt(now.minusHours(2));
        } else {
            art.setArtStatus(Art.STATUS_ACTIVE);
        }
        return artRepository.saveAndFlush(art);
    }

    private void refreshSafeAuctionWindow(Art art, DemoArt spec, LocalDateTime now) {
        if ((spec.role() != DemoArtRole.ONGOING && spec.role() != DemoArtRole.UPCOMING)
                || !isSafeToRefresh(art)) {
            return;
        }
        applyAuctionWindow(art, spec, now);
        art.setArtStatus(Art.STATUS_ACTIVE);
        art.setClosedAt(null);
        art.setCurrentPrice(art.getStartPrice());
        artRepository.save(art);
    }

    private boolean isSafeToRefresh(Art art) {
        return art.getWinningBid() == null
                && art.getActivePointHold() == null
                && !bidRepository.existsByArt(art)
                && !orderRepository.existsByArtArtId(art.getArtId());
    }

    private void applyAuctionWindow(Art art, DemoArt spec, LocalDateTime now) {
        switch (spec.role()) {
            case ONGOING -> {
                art.setBidStartTime(now.minusDays(5));
                art.setClosingTime(now.plusHours(spec.hoursToClose()));
            }
            case UPCOMING -> {
                art.setBidStartTime(now.plusDays(2));
                art.setClosingTime(now.plusDays(5));
            }
            case SOLD -> {
                art.setBidStartTime(now.minusDays(5));
                art.setClosingTime(now.plusHours(1));
            }
            case UNSOLD -> {
                art.setBidStartTime(now.minusDays(5));
                art.setClosingTime(now.minusHours(2));
            }
        }
    }

    private void closeSoldArt(Art art, int bidPrice, User buyer) {
        BidCreateRequestDto request = new BidCreateRequestDto();
        request.setBidPrice(bidPrice);
        bidService.createBid(art.getArtId(), buyer.getUserId(), request);
        art.setClosingTime(LocalDateTime.now(clock).minusMinutes(1));
        artRepository.saveAndFlush(art);
        auctionCloseService.closeAuction(art.getArtId());
    }

    private User buyerFor(String name, User one, User two, User three) {
        return switch (Math.floorMod(name.hashCode(), 3)) {
            case 0 -> one;
            case 1 -> two;
            default -> three;
        };
    }

    private static DemoArt art(Artist artist, String name, String image, ArtFormat format,
                               ArtCategory category, int startPrice, int increment, int hoursToClose,
                               DemoArtRole role) {
        return new DemoArt(artist, name, image, format, category, startPrice, increment, hoursToClose, role);
    }

    private record DemoArt(Artist artist, String name, String image, ArtFormat format,
                           ArtCategory category, int startPrice, int increment, int hoursToClose,
                           DemoArtRole role) { }

    private enum DemoArtRole {
        ONGOING,
        UPCOMING,
        SOLD,
        UNSOLD
    }

    @Configuration
    @Profile("local-demo")
    @ConditionalOnProperty(prefix = "dailyatelier.local-demo", name = "enabled", havingValue = "true")
    @EnableConfigurationProperties(LocalDemoSeedProperties.class)
    static class RunnerConfiguration {
        @Bean
        ApplicationRunner localDemoDataSeedRunner(LocalDemoDataSeeder seeder) {
            return args -> seeder.seed();
        }
    }
}
