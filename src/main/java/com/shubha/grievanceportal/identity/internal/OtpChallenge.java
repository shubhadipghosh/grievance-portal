package com.shubha.grievanceportal.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** One OTP sent to a phone. Only the hash of the code is stored, never the code. */
@Entity
@Table(name = "otp_challenge")
public class OtpChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false, updatable = false)
    private UUID id;

    @Column(name = "phone", nullable = false, length = 15, updatable = false)
    private String phone;

    @Column(name = "code_hash", nullable = false, updatable = false)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 20, updatable = false)
    private OtpPurpose purpose;

    @Column(name = "attempts", nullable = false)
    private short attempts;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "burned_reason", length = 30)
    private BurnReason burnedReason;

    // Personal data under DPDP: kept only to enforce the per-IP send cap.
    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "request_ip", updatable = false)
    private InetAddress requestIp;

    @Column(name = "user_agent", length = 255, updatable = false)
    private String userAgent;

    // DB default now(); null on a freshly persisted instance until reloaded.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected OtpChallenge() {
    }

    public OtpChallenge(String phone, String codeHash, OtpPurpose purpose, OffsetDateTime expiresAt) {
        this(phone, codeHash, purpose, expiresAt, null, null);
    }

    /**
     * @param requestIp nullable
     * @param userAgent nullable, at most 255 characters
     */
    public OtpChallenge(String phone, String codeHash, OtpPurpose purpose, OffsetDateTime expiresAt,
                        InetAddress requestIp, String userAgent) {
        this.phone = Objects.requireNonNull(phone, "phone");
        this.codeHash = Objects.requireNonNull(codeHash, "codeHash");
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.requestIp = requestIp;
        this.userAgent = userAgent;
    }

    public UUID getId() {
        return id;
    }

    public String getPhone() {
        return phone;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public OtpPurpose getPurpose() {
        return purpose;
    }

    public short getAttempts() {
        return attempts;
    }

    public void recordAttempt() {
        attempts++;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getConsumedAt() {
        return consumedAt;
    }

    public OffsetDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public BurnReason getBurnedReason() {
        return burnedReason;
    }

    /**
     * Burns the challenge without a successful verification. consumed_at and
     * burned_reason are set together, as consumed_has_reason requires.
     *
     * @throws IllegalArgumentException reason is VERIFIED; use {@link #verify}
     */
    public void consume(OffsetDateTime at, BurnReason reason) {
        if (Objects.requireNonNull(reason, "reason") == BurnReason.VERIFIED) {
            throw new IllegalArgumentException("Use verify() to record a successful verification");
        }
        finish(at, reason);
    }

    /** The code matched: consumes the challenge and records when it was verified. */
    public void verify(OffsetDateTime at) {
        finish(at, BurnReason.VERIFIED);
        this.verifiedAt = at;
    }

    private void finish(OffsetDateTime at, BurnReason reason) {
        if (consumedAt != null) {
            throw new IllegalStateException("OTP challenge " + id + " already consumed");
        }
        this.consumedAt = Objects.requireNonNull(at, "at");
        this.burnedReason = reason;
    }

    public InetAddress getRequestIp() {
        return requestIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OtpChallenge other)) {
            return false;
        }
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        // Constant per type: stable across persist and safe for Hibernate proxies.
        return OtpChallenge.class.hashCode();
    }

    @Override
    public String toString() {
        // No phone/code hash: keep PII and secrets out of logs.
        return "OtpChallenge[id=" + id + ", purpose=" + purpose + "]";
    }
}
