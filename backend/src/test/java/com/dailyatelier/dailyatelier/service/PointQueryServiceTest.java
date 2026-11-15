package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.exception.PointApiException;
import com.dailyatelier.dailyatelier.repository.PointAccountRepository;
import com.dailyatelier.dailyatelier.repository.PointChargeRepository;
import com.dailyatelier.dailyatelier.repository.PointTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointQueryServiceTest {

    @Mock
    private PointAccountRepository accountRepository;

    @Mock
    private PointTransactionRepository transactionRepository;

    @Mock
    private PointChargeRepository chargeRepository;

    @InjectMocks
    private PointQueryService pointQueryService;

    @Test
    void rejectsInvalidTransactionAndChargePagesBeforeRepositoryCall() {
        assertInvalidRequest(() -> pointQueryService.getTransactions("buyer", -1, 20));
        assertInvalidRequest(() -> pointQueryService.getCharges("buyer", -1, 20));
        assertInvalidRequest(() -> pointQueryService.getTransactions("buyer", 0, 0));
        assertInvalidRequest(() -> pointQueryService.getCharges("buyer", 0, 51));

        verify(transactionRepository, never())
                .findByUserIdOrderByCreatedAtDescTransactionIdDesc(eq("buyer"), any());
        verify(chargeRepository, never())
                .findByUserIdOrderByCreatedAtDescChargeIdDesc(eq("buyer"), any());
    }

    @Test
    void keepsValidPageAndSizeWithoutNormalization() {
        when(transactionRepository.findByUserIdOrderByCreatedAtDescTransactionIdDesc(
                eq("buyer"), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(chargeRepository.findByUserIdOrderByCreatedAtDescChargeIdDesc(
                eq("buyer"), any(Pageable.class)))
                .thenReturn(Page.empty());

        pointQueryService.getTransactions("buyer", 0, 20);
        pointQueryService.getCharges("buyer", 1, 50);

        verify(transactionRepository).findByUserIdOrderByCreatedAtDescTransactionIdDesc(
                eq("buyer"), argThat(pageable ->
                        pageable.getPageNumber() == 0 && pageable.getPageSize() == 20));
        verify(chargeRepository).findByUserIdOrderByCreatedAtDescChargeIdDesc(
                eq("buyer"), argThat(pageable ->
                        pageable.getPageNumber() == 1 && pageable.getPageSize() == 50));
    }

    private void assertInvalidRequest(ThrowingCallable callable) {
        assertThatThrownBy(callable::call)
                .isInstanceOf(PointApiException.class)
                .satisfies(error -> {
                    PointApiException exception = (PointApiException) error;
                    assertThat(exception.getCode()).isEqualTo("INVALID_POINT_REQUEST");
                    assertThat(exception.getMessage())
                            .isEqualTo("페이지는 0 이상, 페이지 크기는 1 이상 50 이하여야 합니다.");
                });
    }

    @FunctionalInterface
    private interface ThrowingCallable {
        void call();
    }
}
