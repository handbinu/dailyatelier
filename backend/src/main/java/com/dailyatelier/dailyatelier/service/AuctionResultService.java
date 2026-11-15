package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.WinningArtResponseDto;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuctionResultService {
    private static final int MAX_PAGE_SIZE = 50;

    private final ArtRepository artRepository;

    public Page<WinningArtResponseDto> getMyWins(String userId, int page, int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE)
        );
        return artRepository.findWinningArtsByUserId(
                userId,
                Art.STATUS_SOLD,
                pageable
        );
    }
}
