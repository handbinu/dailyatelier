package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.*;
import com.dailyatelier.dailyatelier.exception.OrderApiException;
import com.dailyatelier.dailyatelier.repository.PointAccountRepository;
import com.dailyatelier.dailyatelier.repository.PointHoldRepository;
import com.dailyatelier.dailyatelier.repository.PointTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OrderPointLedgerService {
    private final PointAccountRepository accountRepository;
    private final PointHoldRepository holdRepository;
    private final PointTransactionRepository transactionRepository;

    public void commit(Order order, String idempotencyKey, LocalDateTime now) {
        requireInternalPoint(order);
        PointAccount account = lockedAccount(order);
        PointHold hold = lockedHold(order);
        validateHold(order, hold, PointHoldStatus.HELD);
        long amount = hold.getAmount();
        account.commit(amount, now);
        PointTransaction transaction = transactionRepository.saveAndFlush(
                PointTransaction.record(
                        account.getUserId(), PointTransactionType.COMMIT, amount,
                        0, -amount, account.getAvailableBalance(), account.getHeldBalance(),
                        PointReferenceType.ORDER, order.getOrderId().toString(),
                        requireKey(idempotencyKey), null, "ORDER_PAID",
                        "낙찰 주문 포인트 결제", now));
        hold.commit(order.getOrderId(), now);
        holdRepository.save(hold);
    }

    public void release(
            Order order,
            PointHoldReleaseReason reason,
            String idempotencyKey,
            LocalDateTime now) {
        PointAccount account = lockedAccount(order);
        PointHold hold = lockedHold(order);
        if (hold.getStatus() == PointHoldStatus.RELEASED) {
            return;
        }
        validateHold(order, hold, PointHoldStatus.HELD);
        account.release(hold.getAmount(), now);
        transactionRepository.saveAndFlush(PointTransaction.record(
                account.getUserId(), PointTransactionType.RELEASE, hold.getAmount(),
                hold.getAmount(), -hold.getAmount(),
                account.getAvailableBalance(), account.getHeldBalance(),
                PointReferenceType.ORDER, order.getOrderId().toString(),
                requireKey(idempotencyKey), null, reason.name(),
                "낙찰 주문 예치 해제", now));
        hold.release(reason, now);
        holdRepository.save(hold);
    }

    public void refund(
            Order order,
            String idempotencyKey,
            OrderRefundReason reason,
            LocalDateTime now) {
        PointAccount account = lockedAccount(order);
        PointHold hold = lockedHold(order);
        validateHold(order, hold, PointHoldStatus.COMMITTED);
        PointTransaction commit = transactionRepository
                .findByReferenceTypeAndReferenceIdAndType(
                        PointReferenceType.ORDER,
                        order.getOrderId().toString(),
                        PointTransactionType.COMMIT)
                .orElseThrow(() -> conflict("POINT_LEDGER_INTEGRITY", "주문 결제 원장을 찾을 수 없습니다."));
        account.credit(hold.getAmount(), now);
        transactionRepository.saveAndFlush(PointTransaction.record(
                account.getUserId(), PointTransactionType.REFUND, hold.getAmount(),
                hold.getAmount(), 0,
                account.getAvailableBalance(), account.getHeldBalance(),
                PointReferenceType.ORDER, order.getOrderId().toString(),
                requireKey(idempotencyKey), commit.getTransactionId(), reason.name(),
                "낙찰 주문 포인트 환불", now));
    }

    private PointAccount lockedAccount(Order order) {
        return accountRepository.findByUserIdForUpdate(order.getBuyer().getUserId())
                .orElseThrow(() -> conflict("POINT_ACCOUNT_NOT_FOUND", "포인트 계정이 없습니다."));
    }

    private PointHold lockedHold(Order order) {
        PointHold activeHold = order.getArt().getActivePointHold();
        if (activeHold == null || activeHold.getHoldId() == null) {
            throw conflict("POINT_HOLD_INTEGRITY", "낙찰 예치를 찾을 수 없습니다.");
        }
        return holdRepository.findByIdForUpdate(activeHold.getHoldId())
                .orElseThrow(() -> conflict("POINT_HOLD_INTEGRITY", "낙찰 예치를 찾을 수 없습니다."));
    }

    private void validateHold(Order order, PointHold hold, PointHoldStatus status) {
        if (hold.getStatus() != status
                || !Objects.equals(hold.getUser().getUserId(), order.getBuyer().getUserId())
                || hold.getAmount() != order.getWinningPrice().longValue()
                || !Objects.equals(hold.getLatestBid().getBidId(), order.getWinningBid().getBidId())) {
            throw conflict("POINT_HOLD_INTEGRITY", "낙찰 정보와 예치 정보가 일치하지 않습니다.");
        }
    }

    private void requireInternalPoint(Order order) {
        if (order.getPaymentMethod() != OrderPaymentMethod.INTERNAL_POINT) {
            throw conflict("PAYMENT_METHOD_NOT_SUPPORTED", "내부 포인트 결제만 지원합니다.");
        }
    }

    private String requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("멱등성 키는 필수입니다");
        }
        return key.trim();
    }

    private OrderApiException conflict(String code, String message) {
        return new OrderApiException(HttpStatus.CONFLICT, code, message);
    }
}
