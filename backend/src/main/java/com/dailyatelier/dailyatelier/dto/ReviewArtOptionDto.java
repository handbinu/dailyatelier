package com.dailyatelier.dailyatelier.dto;

import com.dailyatelier.dailyatelier.entity.Art;

public record ReviewArtOptionDto(Long artId, String artName) {
    public static ReviewArtOptionDto from(Art art) {
        return new ReviewArtOptionDto(art.getArtId(), art.getName());
    }
}
