package com.dailyatelier.dailyatelier.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "inquiry")
@Getter @Setter
@NoArgsConstructor
public class Inquiry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long inquiryId;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private String answer;

    @Column
    private LocalDateTime answeredAt;
}
