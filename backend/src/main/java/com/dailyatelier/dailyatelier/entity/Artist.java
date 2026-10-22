package com.dailyatelier.dailyatelier.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "artist")
@Getter @Setter
public class Artist {
    @Id
    @UuidGenerator
    @Column(name = "artist_code", length = 36, updatable = false, nullable = false)
    private String artistCode;

    @OneToOne(optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(length = 50)
    private String artistName;
    // 작가 전용 활동명 (미입력 시 nickname으로 자동 세팅)
    @Column(length = 300)
    private String artistIntro;

    @Column(length = 100)
    private String homepage;

    @Column(length = 100)
    private String artistSns;
}
