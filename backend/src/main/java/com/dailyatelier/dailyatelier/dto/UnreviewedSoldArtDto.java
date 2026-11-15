package com.dailyatelier.dailyatelier.dto;

import com.dailyatelier.dailyatelier.entity.Art;

public record UnreviewedSoldArtDto(
        Long artId,
        String artName,
        String artImage) {

    public static UnreviewedSoldArtDto from(Art art) {
        return new UnreviewedSoldArtDto(
                art.getArtId(),
                art.getName(),
                art.getImgPath()
        );
    }
}
