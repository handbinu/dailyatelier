package com.dailyatelier.dailyatelier.payment;

import com.dailyatelier.dailyatelier.entity.PaymentProvider;
import org.springframework.stereotype.Component;

@Component
public class InternalPointPaymentProvider implements PointPaymentProvider {
    @Override
    public PaymentProvider provider() {
        return PaymentProvider.INTERNAL;
    }

    @Override
    public void validate(PaymentApproval approval) {
        if (approval.provider() != PaymentProvider.INTERNAL) {
            throw new IllegalArgumentException("INTERNAL 결제 제공자 요청이 아닙니다");
        }
        if (!approval.trustedInternalRequest()) {
            throw new SecurityException("내부 충전 승인 권한이 없습니다");
        }
        if (approval.requestedAmount() <= 0
                || approval.requestedAmount() != approval.paidAmount()) {
            throw new IllegalArgumentException("충전 승인 금액이 올바르지 않습니다");
        }
    }
}
