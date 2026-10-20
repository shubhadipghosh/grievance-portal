package com.shubha.grievanceportal.identity.internal;

import com.shubha.grievanceportal.identity.AppUser;
import com.shubha.grievanceportal.identity.OtpSendResult;
import com.shubha.grievanceportal.identity.OtpService;
import com.shubha.grievanceportal.identity.OtpVerifyResult;
import com.shubha.grievanceportal.identity.UserStatus;
import com.shubha.grievanceportal.identity.UserType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
class OtpServiceImpl implements OtpService {

    private static final Pattern INDIAN_MOBILE = Pattern.compile("\\+91[6-9]\\d{9}");
    private static final int USER_AGENT_MAX = 255;
    private static final OtpVerifyResult FAILED = new OtpVerifyResult.Failed();

    private final OtpChallengeRepository challenges;
    private final AppUserRepository users;
    private final RateLimiter rateLimiter;
    private final OtpSender sender;
    private final PasswordEncoder encoder;
    private final OtpProperties props;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final SecureRandom random = new SecureRandom();
    // Hash checked when there is no live code, so that path costs the same as a wrong code.
    private final String dummyHash;

    OtpServiceImpl(OtpChallengeRepository challenges, AppUserRepository users, RateLimiter rateLimiter,
                   OtpSender sender, PasswordEncoder encoder, OtpProperties props, Clock clock,
                   PlatformTransactionManager transactionManager) {
        this.challenges = challenges;
        this.users = users;
        this.rateLimiter = rateLimiter;
        this.sender = sender;
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
        this.dummyHash = encoder.encode("0".repeat(props.codeLength()));
    }

    @Override
    public OtpSendResult sendLoginOtp(String phone, String requestIp, String userAgent) {
        if (!isPlausibleMobile(phone)) {
            return new OtpSendResult.InvalidPhone();
        }
        // ofLiteral never does a DNS lookup, unlike getByName.
        InetAddress ip = InetAddress.ofLiteral(Objects.requireNonNull(requestIp, "requestIp"));
        Instant now = clock.instant();
        String code = newCode();

        OtpSendResult result = tx.execute(status -> {
            Optional<Duration> blocked = rateLimiter.tryAcquire(RateLimiter.Scope.PHONE, phone,
                            Duration.ofDays(1), props.maxSendsPerPhonePerDay(), props.resendCooldown(), now)
                    .or(() -> rateLimiter.tryAcquire(RateLimiter.Scope.IP, ip.getHostAddress(),
                            Duration.ofHours(1), props.maxSendsPerIpPerHour(), Duration.ZERO, now));
            if (blocked.isPresent()) {
                // Undo a phone count already taken when the IP cap is what blocked.
                status.setRollbackOnly();
                return new OtpSendResult.RateLimited(blocked.get());
            }

            OffsetDateTime at = now.atOffset(ZoneOffset.UTC);
            challenges.findLiveForUpdate(phone, OtpPurpose.LOGIN).ifPresent(live -> {
                live.consume(at, BurnReason.SUPERSEDED);
                // Hibernate runs inserts before updates; uq_live_otp_per_phone needs the update first.
                challenges.flush();
            });
            challenges.save(new OtpChallenge(phone, encoder.encode(code), OtpPurpose.LOGIN,
                    at.plus(props.validity()), ip, truncate(userAgent)));
            return new OtpSendResult.Sent();
        });

        // After commit: never deliver a code that was not stored.
        if (result instanceof OtpSendResult.Sent) {
            sender.send(phone, code);
        }
        return result;
    }

    @Override
    public OtpVerifyResult verifyLoginOtp(String phone, String code, String requestIp) {
        Objects.requireNonNull(code, "code");
        if (!isPlausibleMobile(phone)) {
            encoder.matches(code, dummyHash);
            return FAILED;
        }
        return tx.execute(status -> {
            OffsetDateTime now = OffsetDateTime.now(clock);
            Optional<OtpChallenge> live = challenges.findLiveForUpdate(phone, OtpPurpose.LOGIN);
            if (live.isEmpty()) {
                encoder.matches(code, dummyHash);
                return FAILED;
            }
            OtpChallenge challenge = live.get();
            if (!challenge.getExpiresAt().isAfter(now)) {
                challenge.consume(now, BurnReason.EXPIRED);
                encoder.matches(code, dummyHash);
                return FAILED;
            }
            if (!encoder.matches(code, challenge.getCodeHash())) {
                challenge.recordAttempt();
                if (challenge.getAttempts() >= props.maxAttempts()) {
                    challenge.consume(now, BurnReason.MAX_ATTEMPTS);
                }
                return FAILED;
            }

            challenge.verify(now);
            AppUser user = users.findByPhone(phone)
                    .orElseGet(() -> users.save(AppUser.citizen(phone, null)));
            // A staff member's phone must not open a citizen session for them, and
            // a locked or disabled citizen does not get one either.
            if (user.getUserType() != UserType.CITIZEN || user.getStatus() != UserStatus.ACTIVE) {
                return FAILED;
            }
            return new OtpVerifyResult.Verified(user.getId());
        });
    }

    private static boolean isPlausibleMobile(String phone) {
        return phone != null && INDIAN_MOBILE.matcher(phone).matches();
    }

    private String newCode() {
        int bound = (int) Math.pow(10, props.codeLength());
        return String.format("%0" + props.codeLength() + "d", random.nextInt(bound));
    }

    private static String truncate(String userAgent) {
        return userAgent == null || userAgent.length() <= USER_AGENT_MAX
                ? userAgent : userAgent.substring(0, USER_AGENT_MAX);
    }
}
