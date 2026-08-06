package com.dailyatelier.dailyatelier.dto;

import lombok.Getter;

@Getter
public class ArtistDetailResponseDto {
    private final String artistId;
    private final String profileImagePath;
    private final String artistName;
    private final String artistIntro;
    private final long activeArtCount;

    public ArtistDetailResponseDto(
            String artistId,
            String artistName,
            String artistIntro,
            long activeArtCount) {
        this.artistId = artistId;
        this.profileImagePath = ArtistSummaryResponseDto.DEFAULT_PROFILE_IMAGE_PATH;
        this.artistName = artistName;
        this.artistIntro = artistIntro;
        this.activeArtCount = activeArtCount;
    }
}
