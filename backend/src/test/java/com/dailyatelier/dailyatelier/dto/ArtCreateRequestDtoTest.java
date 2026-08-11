package com.dailyatelier.dailyatelier.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArtCreateRequestDtoTest {
    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void requiresFormatAndCategory() {
        ArtCreateRequestDto request = new ArtCreateRequestDto();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("format", "category");
    }
}
