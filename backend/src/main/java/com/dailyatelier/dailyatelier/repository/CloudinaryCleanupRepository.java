package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.entity.CloudinaryCleanup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CloudinaryCleanupRepository
        extends JpaRepository<CloudinaryCleanup, Long> {
}
