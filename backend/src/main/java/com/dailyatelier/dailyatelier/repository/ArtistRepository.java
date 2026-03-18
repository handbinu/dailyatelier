package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.entity.Artist;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtistRepository extends JpaRepository<Artist, String> {
}
