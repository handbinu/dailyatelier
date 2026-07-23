package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Artist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtRepository extends JpaRepository<Art, Long> {
    Page<Art> findByArtStatus(Integer artStatus, Pageable pageable);

    Page<Art> findByArtist(Artist artist, Pageable pageable);
}
