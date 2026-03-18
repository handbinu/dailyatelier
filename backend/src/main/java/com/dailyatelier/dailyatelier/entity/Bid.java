package com.dailyatelier.dailyatelier.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "bid")
@Getter @Setter
@NoArgsConstructor
public class Bid {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bidId;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "art_id")
    private Art art;

    @Column(nullable = false)
    private Integer bidPrice;

    @Column(nullable = false)
    private LocalDateTime bidTime;
}
