package com.shubha.grievanceportal.identity.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {

    // Served by idx_otp_phone_recent (phone, created_at DESC).
    Optional<OtpChallenge> findFirstByPhoneAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            String phone, OtpPurpose purpose);
}
