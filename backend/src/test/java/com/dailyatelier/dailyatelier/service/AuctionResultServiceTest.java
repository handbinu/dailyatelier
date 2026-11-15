package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.WinningArtResponseDto;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionResultServiceTest {

    @Mock
    private ArtRepository artRepository;

    @InjectMocks
    private AuctionResultService auctionResultService;

    @Test
    void normalizesPageAndCapsPageSize() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(artRepository.findWinningArtsByUserId(
                eq("winner"),
                eq(Art.STATUS_SOLD),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(Page.empty());

        auctionResultService.getMyWins("winner", -2, 100);

        verify(artRepository).findWinningArtsByUserId(
                eq("winner"),
                eq(Art.STATUS_SOLD),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
    }
}
