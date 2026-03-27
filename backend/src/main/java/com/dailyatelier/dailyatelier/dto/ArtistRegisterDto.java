package com.dailyatelier.dailyatelier.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ArtistRegisterDto {

    private String userId;
    private String password;
    private String name;
    private String nickname;
    private String phoneNumber;
    private String email;
    private String artistName;
    private String homepage;
    private String artistSns;
}
