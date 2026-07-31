package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.PointChargeResponseDto;
import com.dailyatelier.dailyatelier.dto.PointSummaryResponseDto;
import com.dailyatelier.dailyatelier.dto.PointTransactionResponseDto;
import com.dailyatelier.dailyatelier.entity.PointAccount;
import com.dailyatelier.dailyatelier.repository.PointAccountRepository;
import com.dailyatelier.dailyatelier.repository.PointChargeRepository;
import com.dailyatelier.dailyatelier.repository.PointTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointQueryService {
    private static final int MAX_PAGE_SIZE = 50;
    private final PointAccountRepository accountRepository;
    private final PointTransactionRepository transactionRepository;
    private final PointChargeRepository chargeRepository;

    public PointSummaryResponseDto getSummary(String userId) {
        PointAccount account = accountRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("포인트 계정이 없습니다"));
        return new PointSummaryResponseDto(
                account.getAvailableBalance(),
                account.getHeldBalance());
    }

    public Page<PointTransactionResponseDto> getTransactions(
            String userId, int page, int size) {
        return transactionRepository
                .findByUserIdOrderByCreatedAtDescTransactionIdDesc(
                        userId, PageRequest.of(page, normalizedSize(size)))
                .map(PointTransactionResponseDto::from);
    }

    public Page<PointChargeResponseDto> getCharges(
            String userId, int page, int size) {
        return chargeRepository
                .findByUserIdOrderByCreatedAtDescChargeIdDesc(
                        userId, PageRequest.of(page, normalizedSize(size)))
                .map(PointChargeResponseDto::from);
    }

    private int normalizedSize(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }
}
