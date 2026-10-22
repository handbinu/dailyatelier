package com.dailyatelier.dailyatelier.payment;

import com.dailyatelier.dailyatelier.entity.PaymentCallbackEvent;

@FunctionalInterface
public interface PaymentCallbackProcessor {
    void process(PaymentCallbackEvent event);
}
