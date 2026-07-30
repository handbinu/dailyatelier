package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.PointAccount;
import com.dailyatelier.dailyatelier.entity.PointTransaction;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.repository.PointAccountRepository;
import com.dailyatelier.dailyatelier.repository.PointTransactionRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PointAccountService {

    private final UserRepository userRepository;
    private final PointAccountRepository pointAccountRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final Clock clock;

    @Transactional
    public PointAccount initializeAccount(String userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "사용자를 찾을 수 없습니다"
                ));

        return pointAccountRepository.findById(userId)
                .orElseGet(() -> createAccount(user));
    }

    @Transactional
    public long getAvailableBalance(String userId) {
        return pointAccountRepository.findById(userId)
                .map(PointAccount::getAvailableBalance)
                .orElseThrow(() -> new IllegalStateException(
                        "포인트 계정이 초기화되지 않았습니다: " + userId
                ));
    }

    private PointAccount createAccount(User user) {
        long openingBalance = user.getReserve() == null
                ? 0L
                : user.getReserve().longValue();
        if (openingBalance < 0) {
            throw new IllegalStateException(
                    "기존 포인트 잔액은 음수일 수 없습니다: " + user.getUserId()
            );
        }

        LocalDateTime now = LocalDateTime.now(clock);
        PointAccount account = pointAccountRepository.save(
                PointAccount.open(user, openingBalance, now)
        );
        if (openingBalance > 0) {
            pointTransactionRepository.save(
                    PointTransaction.openingBalance(
                            user.getUserId(),
                            openingBalance,
                            now
                    )
            );
        }
        return account;
    }
}
