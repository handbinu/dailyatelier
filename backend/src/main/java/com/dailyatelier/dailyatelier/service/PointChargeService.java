package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.*;
import com.dailyatelier.dailyatelier.payment.PaymentApproval;
import com.dailyatelier.dailyatelier.payment.PointPaymentProvider;
import com.dailyatelier.dailyatelier.repository.PointAccountRepository;
import com.dailyatelier.dailyatelier.repository.PointChargeRepository;
import com.dailyatelier.dailyatelier.repository.PointTransactionRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import com.dailyatelier.dailyatelier.exception.PointApiException;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PointChargeService {
    private final PointChargeRepository chargeRepository;
    private final PointAccountRepository accountRepository;
    private final PointTransactionRepository transactionRepository;
    private final List<PointPaymentProvider> paymentProviders;
    private final Clock clock;
    private final DemoPointChargePolicy demoChargePolicy;

    public PointChargeService(PointChargeRepository chargeRepository,
                              PointAccountRepository accountRepository,
                              PointTransactionRepository transactionRepository,
                              List<PointPaymentProvider> paymentProviders,
                              Clock clock,
                              DemoPointChargePolicy demoChargePolicy) {
        this.chargeRepository = chargeRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.paymentProviders = paymentProviders;
        this.clock = clock;
        this.demoChargePolicy = demoChargePolicy;
    }

    @Transactional
    public PointCharge create(String userId, PaymentProvider provider, long amount,
                              String idempotencyKey) {
        accountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("포인트 계정이 없습니다"));
        PointCharge existing = chargeRepository
                .findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .orElse(null);
        if (existing != null) {
            if (!existing.matchesRequest(provider, amount)) {
                throw new PointApiException(
                        HttpStatus.CONFLICT,
                        "IDEMPOTENCY_KEY_REUSED",
                        "멱등성 키가 다른 충전 요청에 이미 사용되었습니다");
            }
            return existing;
        }
        if (provider == PaymentProvider.INTERNAL) {
            demoChargePolicy.validateAmount(amount);
        }
        String merchantOrderId = "POINT-" + UUID.randomUUID();
        return chargeRepository.save(PointCharge.pending(
                userId, provider, merchantOrderId, amount, idempotencyKey,
                LocalDateTime.now(clock)));
    }

    @Transactional
    public PointCharge approve(Long chargeId, PaymentApproval approval) {
        PointCharge charge = lockedCharge(chargeId);
        if (charge.getStatus() == PointChargeStatus.PAID) {
            validateSameApproval(charge, approval);
            return charge;
        }
        if (charge.getStatus() != PointChargeStatus.PENDING) {
            throw new IllegalStateException("대기 중인 충전만 승인할 수 있습니다");
        }
        validateSameApproval(charge, approval);
        provider(approval.provider()).validate(approval);

        PointAccount account = accountRepository.findByUserIdForUpdate(charge.getUserId())
                .orElseThrow(() -> new IllegalStateException("포인트 계정이 없습니다"));
        if (charge.getProvider() == PaymentProvider.INTERNAL) {
            demoChargePolicy.validateBalance(
                    account.getAvailableBalance(), account.getHeldBalance(), approval.paidAmount());
        }
        LocalDateTime now = LocalDateTime.now(clock);
        account.credit(approval.paidAmount(), now);
        PointTransaction transaction = transactionRepository.saveAndFlush(
                PointTransaction.record(
                        charge.getUserId(), charge.getProvider() == PaymentProvider.INTERNAL
                                ? PointTransactionType.DEMO_CHARGE
                                : PointTransactionType.CHARGE,
                        approval.paidAmount(), approval.paidAmount(), 0,
                        account.getAvailableBalance(), account.getHeldBalance(),
                        PointReferenceType.CHARGE, chargeId.toString(),
                        "charge:" + chargeId, null,
                        charge.getProvider() == PaymentProvider.INTERNAL
                                ? "DEMO_CHARGE_APPROVED" : "CHARGE_APPROVED",
                        charge.getProvider() == PaymentProvider.INTERNAL
                                ? "데모 포인트 충전" : "포인트 충전 승인", now));
        charge.approve(approval.pgOrderId(), approval.paidAmount(),
                transaction.getTransactionId(), now);
        return charge;
    }

    @Transactional
    public PointCharge fail(Long chargeId, String code, String message) {
        PointCharge charge = lockedCharge(chargeId);
        if (charge.getStatus() == PointChargeStatus.FAILED) return charge;
        charge.fail(code, message, LocalDateTime.now(clock));
        return charge;
    }

    @Transactional
    public PointCharge cancel(Long chargeId) {
        PointCharge charge = lockedCharge(chargeId);
        if (charge.getStatus() == PointChargeStatus.CANCELED) return charge;
        charge.cancel(LocalDateTime.now(clock));
        return charge;
    }

    @Transactional
    public PointCharge refund(Long chargeId) {
        PointCharge charge = lockedCharge(chargeId);
        if (charge.getStatus() == PointChargeStatus.REFUNDED) return charge;
        if (charge.getStatus() != PointChargeStatus.PAID) {
            throw new IllegalStateException("승인된 충전만 환불할 수 있습니다");
        }
        PointAccount account = accountRepository.findByUserIdForUpdate(charge.getUserId())
                .orElseThrow(() -> new IllegalStateException("포인트 계정이 없습니다"));
        LocalDateTime now = LocalDateTime.now(clock);
        account.debit(charge.getPaidAmount(), now);
        PointTransaction reversal = transactionRepository.saveAndFlush(
                PointTransaction.record(
                        charge.getUserId(), PointTransactionType.REFUND,
                        charge.getPaidAmount(), -charge.getPaidAmount(), 0,
                        account.getAvailableBalance(), account.getHeldBalance(),
                        PointReferenceType.CHARGE, chargeId.toString(),
                        "charge-refund:" + chargeId, charge.getChargeTransactionId(),
                        "CHARGE_REFUNDED", "포인트 충전 환불", now));
        charge.refund(reversal.getTransactionId(), now);
        return charge;
    }

    private PointCharge lockedCharge(Long chargeId) {
        return chargeRepository.findByIdForUpdate(chargeId)
                .orElseThrow(() -> new IllegalArgumentException("충전 내역을 찾을 수 없습니다"));
    }

    private void validateSameApproval(PointCharge charge, PaymentApproval approval) {
        if (!charge.matchesRequest(approval.provider(), approval.requestedAmount())
                || approval.paidAmount() != charge.getRequestedAmount()) {
            throw new IllegalStateException("승인 정보가 충전 요청과 일치하지 않습니다");
        }
        if (charge.getPgOrderId() != null
                && !charge.getPgOrderId().equals(approval.pgOrderId())) {
            throw new IllegalStateException("다른 PG 주문번호로 중복 승인할 수 없습니다");
        }
    }

    private PointPaymentProvider provider(PaymentProvider provider) {
        return paymentProviders.stream()
                .filter(candidate -> candidate.provider() == provider)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 결제 제공자입니다"));
    }
}
