package com.dailyatelier.dailyatelier.dto;

import com.dailyatelier.dailyatelier.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public class OrderPageResponseDto {
    private List<OrderSummaryResponseDto> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private Map<OrderStatus, Long> statusCounts;
}
