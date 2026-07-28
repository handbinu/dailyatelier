package com.dailyatelier.dailyatelier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderShippingAddressRequestDto {
    @NotBlank
    @Size(max = 50)
    private String recipientName;

    @NotBlank
    @Size(max = 30)
    private String recipientPhone;

    @NotBlank
    @Pattern(regexp = "\\d{5}")
    private String zipCode;

    @NotBlank
    @Size(max = 100)
    private String address1;

    @Size(max = 100)
    private String address2;

    private boolean saveAsDefault;
}
