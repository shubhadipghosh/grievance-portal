package com.shubha.grievanceportal.identity.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {

    // Served by idx_otp_phone_recent (phone, created_at DESC).
    Optional<OtpChallenge> findFirstByPhoneAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            String phone, OtpPurpose purpose);

    /**
     * The live challenge, row-locked so concurrent verifications of one code
     * serialize: without the lock, parallel wrong guesses lose attempt
     * increments and exceed the attempt cap. At most one row by
     * uq_live_otp_per_phone.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OtpChallenge c where c.phone = :phone and c.purpose = :purpose and c.consumedAt is null")
    Optional<OtpChallenge> findLiveForUpdate(String phone, OtpPurpose purpose);
}
