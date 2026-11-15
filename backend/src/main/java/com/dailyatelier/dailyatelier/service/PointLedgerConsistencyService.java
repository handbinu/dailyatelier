package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.repository.PointConsistencyRepository;
import com.dailyatelier.dailyatelier.repository.PointTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PointLedgerConsistencyService {

    private final PointTransactionRepository pointTransactionRepository;
    private final PointConsistencyRepository pointConsistencyRepository;

    @Transactional(readOnly = true)
    public ConsistencyReport inspect() {
        List<AccountMismatch> mismatches = pointTransactionRepository
                .findLedgerMismatches()
                .stream()
                .map(AccountMismatch::from)
                .toList();
        List<ConsistencyIssue> issues = pointConsistencyRepository
                .findSemanticMismatches()
                .stream()
                .map(ConsistencyIssue::from)
                .toList();
        return new ConsistencyReport(mismatches, issues);
    }

    public record ConsistencyReport(
            List<AccountMismatch> mismatches,
            List<ConsistencyIssue> issues) {
        public ConsistencyReport {
            mismatches = List.copyOf(mismatches);
            issues = List.copyOf(issues);
        }

        public boolean consistent() {
            return mismatches.isEmpty() && issues.isEmpty();
        }
    }

    public enum ConsistencyIssueType {
        USER_WITHOUT_ACCOUNT,
        LEDGER_WITHOUT_ACCOUNT,
        ACTIVE_HOLD_BALANCE_MISMATCH,
        ACTIVE_HOLD_ART_REFERENCE_MISMATCH,
        ART_ACTIVE_HOLD_MISMATCH,
        ORDER_COMMIT_MISMATCH,
        ORDER_REFUND_MISMATCH,
        CHARGE_TRANSACTION_MISMATCH,
        CHARGE_REFUND_MISMATCH
    }

    public record ConsistencyIssue(
            ConsistencyIssueType type,
            String targetType,
            String targetId,
            String reason) {

        private static ConsistencyIssue from(
                PointConsistencyRepository.DiagnosticRow row) {
            return new ConsistencyIssue(
                    ConsistencyIssueType.valueOf(row.type()),
                    row.targetType(),
                    row.targetId(),
                    row.reason()
            );
        }
    }

    public record AccountMismatch(
            String userId,
            long accountAvailableBalance,
            long accountHeldBalance,
            long ledgerAvailableBalance,
            long ledgerHeldBalance) {

        private static AccountMismatch from(
                PointTransactionRepository.PointLedgerMismatch mismatch) {
            return new AccountMismatch(
                    mismatch.getUserId(),
                    mismatch.getAccountAvailableBalance(),
                    mismatch.getAccountHeldBalance(),
                    mismatch.getLedgerAvailableBalance(),
                    mismatch.getLedgerHeldBalance()
            );
        }
    }
}
