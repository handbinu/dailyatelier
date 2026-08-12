package com.dailyatelier.dailyatelier.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ArtUpdateRequestDtoTest {
    private ObjectMapper objectMapper;
    private Validator validator;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder()
                .findAndAddModules()
                .build();
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void distinguishesMissingFieldFromExplicitDescriptionClear() throws Exception {
        ArtUpdateRequestDto missing =
                objectMapper.readValue("{\"material\":\"캔버스\"}", ArtUpdateRequestDto.class);
        ArtUpdateRequestDto clearing =
                objectMapper.readValue("{\"descript\":null}", ArtUpdateRequestDto.class);

        assertThat(missing.isDescriptProvided()).isFalse();
        assertThat(clearing.isDescriptProvided()).isTrue();
        assertThat(clearing.getDescript()).isNull();
        assertThat(validator.validate(missing)).isEmpty();
        assertThat(validator.validate(clearing)).isEmpty();
    }

    @Test
    void distinguishesMissingMinimumBidIncrementFromExplicitNull() throws Exception {
        ArtUpdateRequestDto missing = objectMapper.readValue(
                "{\"material\":\"캔버스\"}", ArtUpdateRequestDto.class);
        ArtUpdateRequestDto explicitNull = objectMapper.readValue(
                "{\"minimumBidIncrement\":null}", ArtUpdateRequestDto.class);

        assertThat(missing.isMinimumBidIncrementProvided()).isFalse();
        assertThat(explicitNull.isMinimumBidIncrementProvided()).isTrue();
        assertThat(messages(validator.validate(explicitNull)))
                .contains("최소 입찰 증분은 필수입니다.");
    }

    @Test
    void validatesProvidedMinimumBidIncrementRangeAndUnit() throws Exception {
        ArtUpdateRequestDto valid = objectMapper.readValue(
                "{\"minimumBidIncrement\":100}", ArtUpdateRequestDto.class);
        ArtUpdateRequestDto invalid = objectMapper.readValue(
                "{\"minimumBidIncrement\":150}", ArtUpdateRequestDto.class);

        assertThat(validator.validate(valid)).isEmpty();
        assertThat(messages(validator.validate(invalid)))
                .contains("최소 입찰 증분은 100원 이상 10,000,000원 이하의 100원 단위여야 합니다.");
    }

    @Test
    void rejectsEmptyPatchAndNullRequiredValues() throws Exception {
        ArtUpdateRequestDto empty =
                objectMapper.readValue("{}", ArtUpdateRequestDto.class);
        ArtUpdateRequestDto nullValues = objectMapper.readValue(
                """
                        {
                          "startPrice": null,
                          "bidStartTime": null,
                          "closingTime": null,
                          "imgPath": null,
                          "format": null,
                          "category": null
                        }
                        """,
                ArtUpdateRequestDto.class
        );

        assertThat(messages(validator.validate(empty)))
                .contains("수정할 필드를 하나 이상 입력해야 합니다.");
        assertThat(messages(validator.validate(nullValues)))
                .contains(
                        "시작가는 필수입니다.",
                        "경매 시작 시각은 필수입니다.",
                        "경매 마감 시각은 필수입니다.",
                        "이미지 경로는 비어 있을 수 없습니다.",
                        "작품 형태는 필수입니다.",
                        "작품 카테고리는 필수입니다."
                );
    }

    @Test
    void rejectsOutOfRangeAndOversizedValues() throws Exception {
        String oversizedDescription = "가".repeat(301);
        ArtUpdateRequestDto request = objectMapper.readValue(
                """
                        {
                          "startPrice": 0,
                          "descript": "%s",
                          "imgPath": "  "
                        }
                        """.formatted(oversizedDescription),
                ArtUpdateRequestDto.class
        );

        Set<ConstraintViolation<ArtUpdateRequestDto>> violations =
                validator.validate(request);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("startPrice", "descript", "providedImgPathValid");
    }

    private Set<String> messages(
            Set<ConstraintViolation<ArtUpdateRequestDto>> violations) {
        return violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(java.util.stream.Collectors.toSet());
    }
}
