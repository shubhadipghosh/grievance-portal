package com.shubha.grievanceportal.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Citizens are identified by phone (OTP login); staff log in with a password.
 * Use {@link #citizen} / {@link #staff} so the table's CHECK constraints hold.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 20, updatable = false)
    private UserType userType;

    @Column(name = "phone", unique = true, length = 15)
    private String phone;

    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    // Nullable for citizens only (staff_needs_name): auto-registration has no name yet.
    @Column(name = "full_name", length = 150)
    private String fullName;

    @Column(name = "preferred_locale", nullable = false, length = 10)
    private String preferredLocale = "en";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    // DB default now(); null on a freshly persisted instance until reloaded.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    // DB default on insert; maintained by onUpdate() afterwards (no DB trigger).
    @Column(name = "updated_at", nullable = false, insertable = false)
    private OffsetDateTime updatedAt;

    protected AppUser() {
    }

    private AppUser(UserType userType, String fullName) {
        this.userType = userType;
        this.fullName = fullName;
    }

    /**
     * @param phone    E.164 format
     * @param fullName nullable: a citizen auto-registered by OTP has not given a name
     */
    public static AppUser citizen(String phone, String fullName) {
        AppUser user = new AppUser(UserType.CITIZEN, fullName);
        user.phone = Objects.requireNonNull(phone, "phone");
        return user;
    }

    public static AppUser staff(String fullName, String email, String passwordHash) {
        AppUser user = new AppUser(UserType.STAFF, Objects.requireNonNull(fullName, "fullName"));
        user.email = normalizeEmail(email);
        user.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        return user;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UserType getUserType() {
        return userType;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        if (userType == UserType.CITIZEN) {
            Objects.requireNonNull(phone, "citizen phone");
        }
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = normalizeEmail(email);
    }

    /**
     * Emails are case-insensitive: stored lowercased, and looked up the same way
     * (V8's email_lowercase CHECK rejects anything else).
     */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.toLowerCase(Locale.ROOT);
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        if (userType == UserType.STAFF) {
            Objects.requireNonNull(passwordHash, "staff passwordHash");
        }
        this.passwordHash = passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = Objects.requireNonNull(fullName, "fullName");
    }

    public String getPreferredLocale() {
        return preferredLocale;
    }

    public void setPreferredLocale(String preferredLocale) {
        this.preferredLocale = Objects.requireNonNull(preferredLocale, "preferredLocale");
    }

    public UserStatus getStatus() {
        return status;
    }

    // Package-private: UserStatusService.changeUserStatus is the only path, and it is audited.
    void setStatus(UserStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AppUser other)) {
            return false;
        }
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        // Constant per type: stable across persist and safe for Hibernate proxies.
        return AppUser.class.hashCode();
    }

    @Override
    public String toString() {
        // No phone/email/password hash: keep PII and secrets out of logs.
        return "AppUser[id=" + id + ", type=" + userType + "]";
    }
}
