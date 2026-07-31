package com.dailyatelier.dailyatelier.payment;

import com.dailyatelier.dailyatelier.entity.PaymentProvider;

public record PaymentApproval(
        PaymentProvider provider,
        String pgOrderId,
        long requestedAmount,
        long paidAmount,
        boolean trustedInternalRequest) {
}
