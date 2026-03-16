package com.dailyatelier.dailyatelier.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name="users")
@Getter @Setter
@NoArgsConstructor
public class User {

    @Id
    @Column(name = "user_id", length = 45)
    private String userId;

    @Column(nullable = false, length = 200)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 10)
    private String nickname;

    @Column(nullable = false, length = 30)
    private String phoneNumber;

    @Column(nullable = false, length = 30)
    private String email;

    @Column(nullable = false)
    private LocalDateTime joinDate;

    @Column(nullable = false)
    private Integer userStatus; // 0:일반 회원, 1: 작가 회원, 2: 관리자

    @Column
    private Integer reserve = 0;
}
