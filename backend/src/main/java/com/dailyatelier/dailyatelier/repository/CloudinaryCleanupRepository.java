package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.entity.CloudinaryCleanup;
import com.dailyatelier.dailyatelier.entity.CloudinaryCleanupStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface CloudinaryCleanupRepository
        extends JpaRepository<CloudinaryCleanup, Long> {
    boolean existsByPublicIdAndStatusIn(
            String publicId,
            Collection<CloudinaryCleanupStatus> statuses);
}
