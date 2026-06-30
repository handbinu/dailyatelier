package com.dailyatelier.dailyatelier.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ProfileUpdateDto {
    private String nickname;
    private String email;
    private String phoneNumber;
    private String currentPw;
    private String newPw;
    private Boolean emailAgree;

    // 주소 정보
    private Integer zipCode;
    private String userAddress1;
    private String userAddress2;

    // 작가 정보
    private String artistName;
    private String artistIntro;
    private String homepage;
    private String artistSns;
}
