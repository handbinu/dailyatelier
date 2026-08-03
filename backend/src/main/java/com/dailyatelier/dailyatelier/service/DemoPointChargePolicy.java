package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.exception.PointApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DemoPointChargePolicy {
    private final Set<Long> allowedAmounts;
    private final long maximumBalance;

    public DemoPointChargePolicy(
            @Value("${dailyatelier.point.demo.allowed-amounts:10000,30000,50000,100000,200000,300000}")
            String allowedAmounts,
            @Value("${dailyatelier.point.demo.maximum-balance:1000000}") long maximumBalance) {
        this.allowedAmounts = Arrays.stream(allowedAmounts.split(","))
                .map(String::trim)
                .map(Long::parseLong)
                .collect(Collectors.toUnmodifiableSet());
        this.maximumBalance = maximumBalance;
    }

    public void validateAmount(long amount) {
        if (!allowedAmounts.contains(amount)) {
            throw new PointApiException(
                    HttpStatus.BAD_REQUEST,
                    "DEMO_CHARGE_AMOUNT_NOT_ALLOWED",
                    "허용된 데모 충전 금액을 선택해 주세요");
        }
    }

    public void validateBalance(long availableBalance, long heldBalance, long amount) {
        long resultingBalance;
        try {
            resultingBalance = Math.addExact(Math.addExact(availableBalance, heldBalance), amount);
        } catch (ArithmeticException exception) {
            throw balanceLimitExceeded();
        }
        if (resultingBalance > maximumBalance) {
            throw balanceLimitExceeded();
        }
    }

    private PointApiException balanceLimitExceeded() {
        return new PointApiException(
                HttpStatus.CONFLICT,
                "DEMO_POINT_BALANCE_LIMIT_EXCEEDED",
                "데모 포인트는 계정당 최대 " + String.format("%,d", maximumBalance) + "P까지 보유할 수 있습니다");
    }
}
