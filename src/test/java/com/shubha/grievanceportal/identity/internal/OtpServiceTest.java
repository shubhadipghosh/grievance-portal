package com.shubha.grievanceportal.identity.internal;

import com.shubha.grievanceportal.TestcontainersConfiguration;
import com.shubha.grievanceportal.identity.AppUser;
import com.shubha.grievanceportal.identity.OtpSendResult;
import com.shubha.grievanceportal.identity.OtpService;
import com.shubha.grievanceportal.identity.OtpVerifyResult;
import com.shubha.grievanceportal.identity.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OTP send and verify against Postgres. Not @Transactional: the concurrency
 * tests need real commits, so tables are cleared before each test instead.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, OtpServiceTest.TestBeans.class})
class OtpServiceTest {

    // Mid-hour and mid-day, so no test straddles a rate-limit window boundary.
    private static final Instant START = Instant.parse("2026-10-20T06:00:00Z");
    private static final String IP = "203.0.113.7";

    @TestConfiguration
    static class TestBeans {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }

        @Bean
        @Primary
        CapturingOtpSender capturingOtpSender() {
            return new CapturingOtpSender();
        }
    }

    static class MutableClock extends Clock {
        private volatile Instant now = START;

        void set(Instant instant) {
            now = instant;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }
    }

    static class CapturingOtpSender implements OtpSender {
        final Map<String, String> lastCode = new ConcurrentHashMap<>();

        @Override
        public void send(String phone, String code) {
            lastCode.put(phone, code);
        }
    }

    @Autowired
    OtpService otp;

    @Autowired
    OtpChallengeRepository challenges;

    @Autowired
    AppUserRepository users;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MutableClock clock;

    @Autowired
    CapturingOtpSender sender;

    @BeforeEach
    void reset() {
        jdbc.sql("DELETE FROM rate_limit").update();
        jdbc.sql("DELETE FROM otp_challenge").update();
        jdbc.sql("DELETE FROM app_user WHERE phone LIKE '+9198765%'").update();
        clock.set(START);
        sender.lastCode.clear();
    }

    static String phone(int n) {
        return "+9198765%05d".formatted(n);
    }

    /** Sends a code and returns it as the sender received it. */
    String send(String phone) {
        assertThat(otp.sendLoginOtp(phone, IP, "test-agent")).isEqualTo(new OtpSendResult.Sent());
        return sender.lastCode.get(phone);
    }

    static String wrong(String code) {
        return code.equals("000000") ? "000001" : "000000";
    }

    List<OtpChallenge> challengesFor(String phone) {
        return challenges.findAll().stream().filter(c -> c.getPhone().equals(phone)).toList();
    }

    // ---------------------------------------------------------------- send

    @Test
    void sendStoresOnlyTheHashAndRequestMetadata() throws Exception {
        String longAgent = "x".repeat(300);
        otp.sendLoginOtp(phone(1), IP, longAgent);
        String code = sender.lastCode.get(phone(1));

        assertThat(code).matches("\\d{6}");
        OtpChallenge stored = challengesFor(phone(1)).getFirst();
        assertThat(stored.getCodeHash()).startsWith("{bcrypt}").doesNotContain(code);
        assertThat(encoder.matches(code, stored.getCodeHash())).isTrue();
        assertThat(stored.getRequestIp()).isEqualTo(InetAddress.ofLiteral(IP));
        assertThat(stored.getUserAgent()).hasSize(255);
        assertThat(stored.getExpiresAt().toInstant()).isEqualTo(START.plus(Duration.ofMinutes(15)));
    }

    @Test
    void implausiblePhonesAreRejectedWithoutSending() {
        for (String bad : List.of("9876500001", "+911234567890", "+9198765000011", "+14155552671", "")) {
            assertThat(otp.sendLoginOtp(bad, IP, null)).isEqualTo(new OtpSendResult.InvalidPhone());
        }
        assertThat(sender.lastCode).isEmpty();
        assertThat(challenges.count()).isZero();
    }

    @Test
    void hostnameAsRequestIpIsRejected() {
        assertThatThrownBy(() -> otp.sendLoginOtp(phone(1), "example.com", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resendWithinCooldownIsRateLimited() {
        send(phone(1));

        clock.advance(Duration.ofSeconds(59));
        assertThat(otp.sendLoginOtp(phone(1), IP, null))
                .isEqualTo(new OtpSendResult.RateLimited(Duration.ofSeconds(1)));

        clock.advance(Duration.ofSeconds(1));
        send(phone(1));
    }

    @Test
    void sixthSendForAPhoneInADayIsRateLimitedUntilTheWindowEnds() {
        for (int i = 0; i < 5; i++) {
            send(phone(1));
            clock.advance(Duration.ofSeconds(61));
        }

        Instant nextUtcDay = Instant.parse("2026-10-21T00:00:00Z");
        assertThat(otp.sendLoginOtp(phone(1), IP, null))
                .isEqualTo(new OtpSendResult.RateLimited(Duration.between(clock.instant(), nextUtcDay)));
    }

    @Test
    void twentyFirstSendFromAnIpInAnHourIsRateLimitedAndDoesNotCountAgainstThePhone() {
        for (int i = 0; i < 20; i++) {
            send(phone(i));
        }

        assertThat(otp.sendLoginOtp(phone(20), IP, null))
                .isEqualTo(new OtpSendResult.RateLimited(Duration.ofHours(1)));
        // The blocked send rolled back its phone count and cooldown.
        assertThat(otp.sendLoginOtp(phone(20), "198.51.100.1", null)).isEqualTo(new OtpSendResult.Sent());
    }

    @Test
    void concurrentSendsForOnePhoneCannotBothPass() throws Exception {
        List<OtpSendResult> results = concurrently(2, i -> otp.sendLoginOtp(phone(1), "198.51.100." + i, null));

        assertThat(results).containsExactlyInAnyOrder(
                new OtpSendResult.Sent(), new OtpSendResult.RateLimited(Duration.ofSeconds(60)));
        assertThat(challengesFor(phone(1))).hasSize(1);
    }

    @Test
    void newSendSupersedesTheLiveCode() {
        String first = send(phone(1));
        clock.advance(Duration.ofSeconds(61));
        String second = send(phone(1));

        assertThat(challengesFor(phone(1)))
                .extracting(OtpChallenge::getBurnedReason)
                .containsExactlyInAnyOrder(BurnReason.SUPERSEDED, null);
        if (!first.equals(second)) {
            assertThat(otp.verifyLoginOtp(phone(1), first, IP)).isEqualTo(new OtpVerifyResult.Failed());
        }
        assertThat(otp.verifyLoginOtp(phone(1), second, IP)).isInstanceOf(OtpVerifyResult.Verified.class);
    }

    @Test
    void uniqueIndexForbidsASecondLiveChallenge() {
        send(phone(1));

        assertThatThrownBy(() -> challenges.saveAndFlush(new OtpChallenge(phone(1), "{noop}x", OtpPurpose.LOGIN,
                OffsetDateTime.now().plusMinutes(5))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------------------------------------------------------------- verify

    @Test
    void thirdWrongCodeBurnsTheChallenge() {
        String code = send(phone(1));
        for (int i = 0; i < 3; i++) {
            assertThat(otp.verifyLoginOtp(phone(1), wrong(code), IP)).isEqualTo(new OtpVerifyResult.Failed());
        }

        OtpChallenge burned = challengesFor(phone(1)).getFirst();
        assertThat(burned.getAttempts()).isEqualTo((short) 3);
        assertThat(burned.getBurnedReason()).isEqualTo(BurnReason.MAX_ATTEMPTS);
        assertThat(otp.verifyLoginOtp(phone(1), code, IP)).isEqualTo(new OtpVerifyResult.Failed());
    }

    @Test
    void concurrentWrongGuessesCannotExceedTheAttemptCap() throws Exception {
        String code = send(phone(1));

        concurrently(8, i -> otp.verifyLoginOtp(phone(1), wrong(code), IP));

        assertThat(challengesFor(phone(1)).getFirst().getBurnedReason()).isEqualTo(BurnReason.MAX_ATTEMPTS);
        assertThat(otp.verifyLoginOtp(phone(1), code, IP)).isEqualTo(new OtpVerifyResult.Failed());
    }

    @Test
    void expiredCodeFailsAndIsBurned() {
        String code = send(phone(1));
        clock.advance(Duration.ofMinutes(15));

        assertThat(otp.verifyLoginOtp(phone(1), code, IP)).isEqualTo(new OtpVerifyResult.Failed());
        assertThat(challengesFor(phone(1)).getFirst().getBurnedReason()).isEqualTo(BurnReason.EXPIRED);
    }

    @Test
    void firstVerifyRegistersACitizenWithoutAName() {
        String code = send(phone(1));

        OtpVerifyResult result = otp.verifyLoginOtp(phone(1), code, IP);

        AppUser user = users.findByPhone(phone(1)).orElseThrow();
        assertThat(result).isEqualTo(new OtpVerifyResult.Verified(user.getId()));
        assertThat(user.getUserType()).isEqualTo(UserType.CITIZEN);
        assertThat(user.getFullName()).isNull();
        OtpChallenge verified = challengesFor(phone(1)).getFirst();
        assertThat(verified.getBurnedReason()).isEqualTo(BurnReason.VERIFIED);
        assertThat(verified.getVerifiedAt()).isNotNull();
    }

    @Test
    void laterVerifyReturnsTheExistingUser() {
        OtpVerifyResult first = otp.verifyLoginOtp(phone(1), send(phone(1)), IP);
        clock.advance(Duration.ofSeconds(61));
        OtpVerifyResult second = otp.verifyLoginOtp(phone(1), send(phone(1)), IP);

        assertThat(second).isEqualTo(first).isInstanceOf(OtpVerifyResult.Verified.class);
        assertThat(jdbc.sql("SELECT count(*) FROM app_user WHERE phone = ?").param(phone(1))
                .query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void wrongCodeLooksTheSameForKnownAndNeverUsedPhones() {
        String known = phone(1);
        otp.verifyLoginOtp(known, send(known), IP);
        clock.advance(Duration.ofSeconds(61));
        String knownCode = send(known);
        String neverUsed = phone(2);
        String neverUsedCode = send(neverUsed);

        OtpVerifyResult forKnown = otp.verifyLoginOtp(known, wrong(knownCode), IP);
        OtpVerifyResult forNeverUsed = otp.verifyLoginOtp(neverUsed, wrong(neverUsedCode), IP);
        OtpVerifyResult withNoCodeSent = otp.verifyLoginOtp(phone(3), "123456", IP);

        assertThat(forKnown).isEqualTo(forNeverUsed).isEqualTo(withNoCodeSent)
                .isEqualTo(new OtpVerifyResult.Failed());
    }

    @Test
    void staffPhoneDoesNotLogInAsCitizen() {
        AppUser staff = AppUser.staff("Officer", null, "{noop}x");
        staff.setPhone(phone(1));
        users.save(staff);

        assertThat(otp.verifyLoginOtp(phone(1), send(phone(1)), IP)).isEqualTo(new OtpVerifyResult.Failed());
        assertThat(users.findByPhone(phone(1)).orElseThrow().getUserType()).isEqualTo(UserType.STAFF);
    }

    // ---------------------------------------------------------------- helpers

    interface Call<T> {
        T run(int i) throws Exception;
    }

    /** Runs n calls released together by a barrier; returns their results. */
    static <T> List<T> concurrently(int n, Call<T> call) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(n);
        try (ExecutorService pool = Executors.newFixedThreadPool(n)) {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    barrier.await();
                    return call.run(index);
                }));
            }
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get());
            }
            return results;
        }
    }
}
