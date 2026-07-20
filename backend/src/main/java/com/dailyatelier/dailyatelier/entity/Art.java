package com.dailyatelier.dailyatelier.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "art")
@Getter @Setter
@NoArgsConstructor
public class Art {

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
    private Integer artStatus; // 0: 진행중, 1: 종료, 2: 낙찰
}
