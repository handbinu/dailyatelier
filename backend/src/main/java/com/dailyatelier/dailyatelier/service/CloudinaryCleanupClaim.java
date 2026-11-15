package com.dailyatelier.dailyatelier.service;

record CloudinaryCleanupClaim(
        Long cleanupId,
        String publicId,
        String resourceType,
        String claimToken,
        int attemptCount) {
}
