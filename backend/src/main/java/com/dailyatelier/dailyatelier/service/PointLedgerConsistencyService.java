package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.repository.PointTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PointLedgerConsistencyService {

    private final PointTransactionRepository pointTransactionRepository;

    @Transactional(readOnly = true)
    public ConsistencyReport inspect() {
        List<AccountMismatch> mismatches = pointTransactionRepository
                .findLedgerMismatches()
                .stream()
                .map(AccountMismatch::from)
                .toList();
        return new ConsistencyReport(mismatches);
    }

    public record ConsistencyReport(List<AccountMismatch> mismatches) {
        public ConsistencyReport {
            mismatches = List.copyOf(mismatches);
        }

        public boolean consistent() {
            return mismatches.isEmpty();
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
