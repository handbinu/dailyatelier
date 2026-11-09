package com.dailyatelier.dailyatelier.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CloudinarySignatureResponseDto {
    private String cloudName;
    private String apiKey;
    private String folder;
    private long timestamp;
    private String signature;
    private String uploadUrl;
}
