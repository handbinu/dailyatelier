package com.dailyatelier.dailyatelier.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ArtistRegisterDto {

    //User 공통 필드
    private String userId;
    private String password;
    private String name;
    private String nickname;
    private String phoneNumber;

    //Artist 공통 필드
    private String artistName;
    private String homepage;
    private String artistSns;
}
