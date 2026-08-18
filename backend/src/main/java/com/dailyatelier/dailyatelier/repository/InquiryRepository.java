package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.entity.Inquiry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {
    Page<Inquiry> findByUser_UserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<Inquiry> findByUser_UserIdAndAnsweredAtIsNullOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<Inquiry> findByUser_UserIdAndAnsweredAtIsNotNullOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<Inquiry> findByAnsweredAtIsNullOrderByCreatedAtDesc(Pageable pageable);

    Page<Inquiry> findByAnsweredAtIsNotNullOrderByCreatedAtDesc(Pageable pageable);

    Page<Inquiry> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
