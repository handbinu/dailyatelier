package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.Order;
import com.dailyatelier.dailyatelier.entity.OrderRefundReason;

public interface OrderPaymentService {
    Order markPaid(Long orderId);

    Order refund(Long orderId, OrderRefundReason reason);
}
