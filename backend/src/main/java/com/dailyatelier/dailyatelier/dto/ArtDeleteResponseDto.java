package com.dailyatelier.dailyatelier.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ArtDeleteResponseDto {
    private Long artId;
    private Action action;
    private Integer artStatus;

    public enum Action {
        DELETED,
        CANCELED
    }
}
