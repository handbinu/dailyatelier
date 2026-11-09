package com.dailyatelier.dailyatelier.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderShippingAddressTest {

    @Test
    void preservesLeadingZeroAndNormalizesText() {
        OrderShippingAddress address = OrderShippingAddress.of(
                "  구매자  ",
                " 010-1234-5678 ",
                "02535",
                " 서울특별시 중랑구 용마산로90길 28 ",
                " 101호 "
        );

        assertThat(address.getRecipientName()).isEqualTo("구매자");
        assertThat(address.getRecipientPhone()).isEqualTo("010-1234-5678");
        assertThat(address.getZipCode()).isEqualTo("02535");
        assertThat(address.getAddress1())
                .isEqualTo("서울특별시 중랑구 용마산로90길 28");
        assertThat(address.getAddress2()).isEqualTo("101호");
    }

    @Test
    void allowsEmptyDetailAddress() {
        OrderShippingAddress address = OrderShippingAddress.of(
                "구매자",
                "010-1234-5678",
                "12345",
                "서울특별시 중랑구",
                " "
        );

        assertThat(address.getAddress2()).isNull();
    }

    @Test
    void rejectsMissingRequiredFieldsAndInvalidZipCode() {
        assertThatThrownBy(() -> OrderShippingAddress.of(
                " ",
                "010-1234-5678",
                "12345",
                "서울특별시 중랑구",
                null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> OrderShippingAddress.of(
                "구매자",
                "010-1234-5678",
                "1234",
                "서울특별시 중랑구",
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("숫자 5자리");

        assertThatThrownBy(() -> OrderShippingAddress.of(
                "구매자",
                "010-1234-5678",
                "12A45",
                "서울특별시 중랑구",
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("숫자 5자리");
    }

    @Test
    void rejectsValuesExceedingColumnLengths() {
        assertThatThrownBy(() -> OrderShippingAddress.of(
                "가".repeat(OrderShippingAddress.RECIPIENT_MAX_LENGTH + 1),
                "010-1234-5678",
                "12345",
                "서울특별시 중랑구",
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("50자 이하");

        assertThatThrownBy(() -> OrderShippingAddress.of(
                "구매자",
                "1".repeat(OrderShippingAddress.PHONE_MAX_LENGTH + 1),
                "12345",
                "서울특별시 중랑구",
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("30자 이하");

        assertThatThrownBy(() -> OrderShippingAddress.of(
                "구매자",
                "010-1234-5678",
                "12345",
                "가".repeat(OrderShippingAddress.ADDRESS_MAX_LENGTH + 1),
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100자 이하");
    }
}
