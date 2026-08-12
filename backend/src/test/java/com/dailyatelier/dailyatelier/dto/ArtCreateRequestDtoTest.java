package com.dailyatelier.dailyatelier.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArtCreateRequestDtoTest {
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();
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

    @Test
    void defaultsMissingMinimumBidIncrementAndRejectsExplicitNull() throws Exception {
        ArtCreateRequestDto missing = objectMapper.readValue("{}", ArtCreateRequestDto.class);
        ArtCreateRequestDto explicitNull = objectMapper.readValue(
                "{\"minimumBidIncrement\":null}",
                ArtCreateRequestDto.class
        );

        assertThat(missing.getMinimumBidIncrement()).isEqualTo(1_000);
        assertThat(validator.validate(missing))
                .extracting(violation -> violation.getPropertyPath().toString())
                .doesNotContain("minimumBidIncrement");
        assertThat(validator.validate(explicitNull))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("minimumBidIncrement");
    }

    @Test
    void validatesMinimumBidIncrementRangeAndUnit() throws Exception {
        for (int valid : new int[]{100, 1_000, 10_000_000}) {
            ArtCreateRequestDto request = objectMapper.readValue(
                    "{\"minimumBidIncrement\":" + valid + "}",
                    ArtCreateRequestDto.class
            );
            assertThat(validator.validate(request))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .doesNotContain("minimumBidIncrementValid");
        }

        for (int invalid : new int[]{99, 150, 10_000_100}) {
            ArtCreateRequestDto request = objectMapper.readValue(
                    "{\"minimumBidIncrement\":" + invalid + "}",
                    ArtCreateRequestDto.class
            );
            assertThat(validator.validate(request))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains("minimumBidIncrementValid");
        }
    }
}
