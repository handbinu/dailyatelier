package com.dailyatelier.dailyatelier.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponseDto {
    private String token;
    private String userId;
    private String nickname;
    private int userStatus; // 0:일반, 1:작가, 2:관리자
}
