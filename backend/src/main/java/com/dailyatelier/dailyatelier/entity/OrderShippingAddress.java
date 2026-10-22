package com.dailyatelier.dailyatelier.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.regex.Pattern;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderShippingAddress {
    public static final int RECIPIENT_MAX_LENGTH = 50;
    public static final int PHONE_MAX_LENGTH = 30;
    public static final int ADDRESS_MAX_LENGTH = 100;
    private static final Pattern ZIP_CODE_PATTERN = Pattern.compile("\\d{5}");

    @Column(name = "recipient_name", length = RECIPIENT_MAX_LENGTH)
    private String recipientName;

    @Column(name = "recipient_phone", length = PHONE_MAX_LENGTH)
    private String recipientPhone;

    @Column(name = "shipping_zip_code", length = 5)
    private String zipCode;

    @Column(name = "shipping_address1", length = ADDRESS_MAX_LENGTH)
    private String address1;

    @Column(name = "shipping_address2", length = ADDRESS_MAX_LENGTH)
    private String address2;

    private OrderShippingAddress(
            String recipientName,
            String recipientPhone,
            String zipCode,
            String address1,
            String address2) {
        this.recipientName = requireText(
                recipientName,
                RECIPIENT_MAX_LENGTH,
                "수령인"
        );
        this.recipientPhone = requireText(
                recipientPhone,
                PHONE_MAX_LENGTH,
                "수령인 연락처"
        );
        this.zipCode = normalizeZipCode(zipCode);
        this.address1 = requireText(address1, ADDRESS_MAX_LENGTH, "기본 주소");
        this.address2 = optionalText(address2, ADDRESS_MAX_LENGTH, "상세 주소");
    }

    public static OrderShippingAddress of(
            String recipientName,
            String recipientPhone,
            String zipCode,
            String address1,
            String address2) {
        return new OrderShippingAddress(
                recipientName,
                recipientPhone,
                zipCode,
                address1,
                address2
        );
    }

    private static String normalizeZipCode(String value) {
        String normalized = requireText(value, 5, "우편번호");
        if (!ZIP_CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("우편번호는 숫자 5자리여야 합니다");
        }
        return normalized;
    }

    private static String requireText(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은(는) 필수입니다");
        }
        return validateLength(value.trim(), maxLength, fieldName);
    }

    private static String optionalText(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return validateLength(value.trim(), maxLength, fieldName);
    }

    private static String validateLength(String value, int maxLength, String fieldName) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + "은(는) " + maxLength + "자 이하여야 합니다"
            );
        }
        return value;
    }
}
