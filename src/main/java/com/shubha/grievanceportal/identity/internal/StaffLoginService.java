package com.shubha.grievanceportal.identity.internal;

import com.shubha.grievanceportal.identity.AppUser;
import com.shubha.grievanceportal.identity.UserStatus;
import com.shubha.grievanceportal.identity.UserType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Staff password login. Unknown email, wrong password and a non-ACTIVE account
 * are all the same {@link Result.Failed}, so the result never reveals that an
 * account exists but is locked. Failures are capped per email (V8).
 */
@Service
class StaffLoginService {

    sealed interface Result {

        record Authenticated(UUID userId) implements Result {
        }

        /** Deliberately carries no reason. */
        record Failed() implements Result {
        }

        record RateLimited(Duration retryAfter) implements Result {
        }
    }

    static final int MAX_FAILURES = 5;
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);

    private static final Result FAILED = new Result.Failed();

    private final AppUserRepository users;
    private final RateLimiter rateLimiter;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final TransactionTemplate tx;
    // Checked when there is no staff account, so that path costs the same as a wrong password.
    private final String dummyHash;

    StaffLoginService(AppUserRepository users, RateLimiter rateLimiter, PasswordEncoder encoder, Clock clock,
                      PlatformTransactionManager transactionManager) {
        this.users = users;
        this.rateLimiter = rateLimiter;
        this.encoder = encoder;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    Result login(String email, String password) {
        Objects.requireNonNull(password, "password");
        // Also the rate-limit key, so case variants of one email share one counter.
        String normalized = AppUser.normalizeEmail(Objects.requireNonNull(email, "email"));
        Instant now = clock.instant();

        return tx.execute(status -> {
            // Counts the attempt before checking it. The counter row stays locked
            // until commit, so concurrent guesses for one email serialize and
            // cannot together exceed the cap.
            Optional<Duration> blocked = rateLimiter.tryAcquire(RateLimiter.Scope.STAFF_LOGIN, normalized,
                    FAILURE_WINDOW, MAX_FAILURES, Duration.ZERO, now);
            if (blocked.isPresent()) {
                return new Result.RateLimited(blocked.get());
            }

            Optional<AppUser> staff = users.findByEmail(normalized).filter(u -> u.getUserType() == UserType.STAFF);
            boolean matches = encoder.matches(password, staff.map(AppUser::getPasswordHash).orElse(dummyHash));
            if (matches && staff.isPresent() && staff.get().getStatus() == UserStatus.ACTIVE) {
                // Only failures count: undo this attempt's increment.
                status.setRollbackOnly();
                return new Result.Authenticated(staff.get().getId());
            }
            return FAILED;
        });
    }
}
