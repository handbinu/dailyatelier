package com.dailyatelier.dailyatelier.payment;

import com.dailyatelier.dailyatelier.entity.PaymentProvider;

public interface PointPaymentProvider {
    PaymentProvider provider();

    void validate(PaymentApproval approval);
}
