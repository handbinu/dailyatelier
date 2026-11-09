package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.dto.ArtSearchCriteria;
import com.dailyatelier.dailyatelier.entity.Art;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public interface ArtSearchRepository {
    Page<Art> search(ArtSearchCriteria criteria, LocalDateTime now, Pageable pageable);
}
