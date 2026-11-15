package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.PointChargeResponseDto;
import com.dailyatelier.dailyatelier.dto.PointSummaryResponseDto;
import com.dailyatelier.dailyatelier.dto.PointTransactionResponseDto;
import com.dailyatelier.dailyatelier.entity.PointAccount;
import com.dailyatelier.dailyatelier.exception.PointApiException;
import com.dailyatelier.dailyatelier.repository.PointAccountRepository;
import com.dailyatelier.dailyatelier.repository.PointChargeRepository;
import com.dailyatelier.dailyatelier.repository.PointTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
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
                        userId, pageRequest(page, size))
                .map(PointTransactionResponseDto::from);
    }

    public Page<PointChargeResponseDto> getCharges(
            String userId, int page, int size) {
        return chargeRepository
                .findByUserIdOrderByCreatedAtDescChargeIdDesc(
                        userId, pageRequest(page, size))
                .map(PointChargeResponseDto::from);
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new PointApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_POINT_REQUEST",
                    "페이지는 0 이상, 페이지 크기는 1 이상 50 이하여야 합니다."
            );
        }
        return PageRequest.of(page, size);
    }
}
