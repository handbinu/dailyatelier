package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArtistRepository extends JpaRepository<Artist, String> {
    Optional<Artist> findByUser(User user);
}

