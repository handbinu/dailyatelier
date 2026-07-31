package com.dailyatelier.dailyatelier.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "art",
        indexes = @Index(
                name = "idx_art_close_candidates",
                columnList = "art_status, closing_time, art_id"
        )
)
@Getter @Setter
@NoArgsConstructor
public class Art {
    public static final int STATUS_ACTIVE = 0;
    public static final int STATUS_UNSOLD = 1;
    public static final int STATUS_SOLD = 2;
    public static final int STATUS_CANCELED = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long artId;

    @ManyToOne
    @JoinColumn(name = "artist_code")
    private Artist artist;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(length = 300)
    private String descript;

    @Column(length = 120)
    private String material;

    @Column(length = 500)
    private String wIntro;

    @Column(nullable = false)
    private Integer startPrice;

    @Column(nullable = false)
    private Integer currentPrice;

    @Column(nullable = false)
    private LocalDateTime bidStartTime;

    @Column(nullable = false)
    private LocalDateTime closingTime;

    @Column(nullable = false)
    private String imgPath;

    @Column(nullable = false)
    private Integer artStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winning_bid_id")
    private Bid winningBid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "active_point_hold_id")
    private PointHold activePointHold;

    private LocalDateTime closedAt;
}
