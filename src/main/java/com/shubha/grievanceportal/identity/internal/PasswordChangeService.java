package com.shubha.grievanceportal.identity.internal;

import com.shubha.grievanceportal.identity.AppUser;
import com.shubha.grievanceportal.identity.UserType;
import com.shubha.grievanceportal.shared.NotAuthorizedException;
import com.shubha.grievanceportal.shared.NotFoundException;
import com.shubha.grievanceportal.shared.PasswordRejectedException;
import com.shubha.grievanceportal.shared.RateLimitedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A staff user replacing their own password, including the admin-generated first one.
 *
 * <p>Wrong current passwords count against the same per-email cap as failed
 * logins (StaffLoginService), so a stolen session gets no extra guesses.
 * Like a login, only failures count: the check runs in its own transaction,
 * which commits the increment on a wrong password and rolls it back on a right one.
 */
@Service
class PasswordChangeService {

    private final AppUserRepository users;
    private final RateLimiter rateLimiter;
    private final PasswordEncoder encoder;
    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final Clock clock;
    private final TransactionTemplate tx;

    PasswordChangeService(AppUserRepository users, RateLimiter rateLimiter, PasswordEncoder encoder,
                          FindByIndexNameSessionRepository<? extends Session> sessions, Clock clock,
                          PlatformTransactionManager transactionManager) {
        this.users = users;
        this.rateLimiter = rateLimiter;
        this.encoder = encoder;
        this.sessions = sessions;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /**
     * Ends the user's other sessions: whoever else held one may be why the password is changing.
     *
     * @param keepSessionId the session making the change, which stays signed in
     * @throws NotAuthorizedException    not a staff account (citizens have no password)
     * @throws RateLimitedException      too many wrong passwords for this account lately
     * @throws PasswordRejectedException current password wrong, or new equals current
     */
    void change(UUID userId, String currentPassword, String newPassword, String keepSessionId) {
        Objects.requireNonNull(currentPassword, "currentPassword");
        Objects.requireNonNull(newPassword, "newPassword");

        // Returns rather than throws: an exception would roll back the increment it must commit.
        Check check = tx.execute(status -> {
            AppUser user = staff(userId);
            // Login counts by email; a staff account without one cannot log in, but keep it capped anyway.
            String key = user.getEmail() != null ? user.getEmail() : user.getId().toString();
            Optional<Duration> limited = rateLimiter.tryAcquire(RateLimiter.Scope.STAFF_LOGIN, key,
                    StaffLoginService.FAILURE_WINDOW, StaffLoginService.MAX_FAILURES, Duration.ZERO,
                    clock.instant());
            if (limited.isPresent()) {
                return new Check.RateLimited(limited.get());
            }
            if (!encoder.matches(currentPassword, user.getPasswordHash())) {
                // Commits: this failure counts.
                return new Check.Wrong();
            }
            // Only failures count: undo this attempt's increment.
            status.setRollbackOnly();
            return new Check.Right();
        });
        switch (check) {
            case Check.RateLimited limited -> throw new RateLimitedException(limited.retryAfter());
            case Check.Wrong _ -> throw new PasswordRejectedException("The current password is incorrect.");
            case Check.Right _ -> {
            }
        }

        tx.executeWithoutResult(status -> {
            // Row-locked and checked again: the password may have changed since the check above.
            AppUser user = users.findForUpdate(userId).orElseThrow(() -> new NotFoundException("User", userId));
            if (!encoder.matches(currentPassword, user.getPasswordHash())) {
                throw new PasswordRejectedException("The current password is incorrect.");
            }
            if (encoder.matches(newPassword, user.getPasswordHash())) {
                throw new PasswordRejectedException("The new password must differ from the current one.");
            }
            user.setChosenPassword(encoder.encode(newPassword));
        });

        // The principal name is the user id. Spring Session deletes in its own transaction.
        sessions.findByPrincipalName(userId.toString()).keySet().stream()
                .filter(id -> !id.equals(keepSessionId))
                .forEach(sessions::deleteById);
    }

    private sealed interface Check {

        record Right() implements Check {
        }

        record Wrong() implements Check {
        }

        record RateLimited(Duration retryAfter) implements Check {
        }
    }

    private AppUser staff(UUID userId) {
        AppUser user = users.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
        if (user.getUserType() != UserType.STAFF) {
            throw new NotAuthorizedException("Only staff accounts have a password");
        }
        return user;
    }
}
