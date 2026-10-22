package com.dailyatelier.dailyatelier.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CloudinarySignatureRequestDto {
    @NotBlank
    private String folder;
}
