package com.dailyatelier.dailyatelier.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UserProfileDto {
    private String userId;
    private String name;
    private String nickname;
    private String phoneNumber;
    private String email;
    private int userStatus;
    private long reserve;
    private long availablePoint;
    private long heldPoint;
    private Boolean emailAgree;

    // 주소 정보
    private String zipCode;
    private String userAddress1;
    private String userAddress2;

    // 작가 정보 (작가인 경우에만 설정됨)
    private String artistName;
    private String artistIntro;
    private String homepage;
    private String artistSns;
}
