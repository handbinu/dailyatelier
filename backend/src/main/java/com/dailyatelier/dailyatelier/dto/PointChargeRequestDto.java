package com.dailyatelier.dailyatelier.dto;

import jakarta.validation.constraints.Min;

public record PointChargeRequestDto(@Min(1000) long amount) {
}
