package com.dailyatelier.dailyatelier.repository;

import java.time.LocalDateTime;

public interface DueBatchCandidate {
    Long getId();
    LocalDateTime getDeadline();
}
