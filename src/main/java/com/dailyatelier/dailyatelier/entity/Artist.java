package com.dailyatelier.dailyatelier.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "artist")
@Getter @Setter
public class Artist {
    @Id
    @Column(name = "artist_code", length = 50)
    private String artistCode;

    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(length = 10)
    private String artistName;

    @Column(length = 300)
    private String artistIntro;

    @Column(length = 100)
    private String homepage;

    @Column(length = 100)
    private String artistSns;
}
