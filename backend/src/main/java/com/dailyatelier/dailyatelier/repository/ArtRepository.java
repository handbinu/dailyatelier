package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.entity.Art;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtRepository extends JpaRepository<Art, Long> {
}
