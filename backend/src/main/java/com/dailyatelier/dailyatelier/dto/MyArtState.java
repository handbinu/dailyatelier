package com.dailyatelier.dailyatelier.dto;

import com.dailyatelier.dailyatelier.entity.Art;

import java.util.List;

public enum MyArtState {
    ALL(List.of(Art.STATUS_ACTIVE, Art.STATUS_UNSOLD, Art.STATUS_SOLD)),
    ACTIVE(List.of(Art.STATUS_ACTIVE)),
    ENDED(List.of(Art.STATUS_UNSOLD, Art.STATUS_SOLD));

    private final List<Integer> artStatuses;

    MyArtState(List<Integer> artStatuses) {
        this.artStatuses = artStatuses;
    }

    public List<Integer> getArtStatuses() {
        return artStatuses;
    }
}
