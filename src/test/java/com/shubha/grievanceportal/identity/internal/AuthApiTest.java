package com.shubha.grievanceportal.identity.internal;

import com.jayway.jsonpath.JsonPath;
import com.shubha.grievanceportal.TestcontainersConfiguration;
import com.shubha.grievanceportal.identity.AppUser;
import com.shubha.grievanceportal.identity.CurrentUser;
import com.shubha.grievanceportal.identity.UserStatus;
import com.shubha.grievanceportal.identity.UserType;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.shubha.grievanceportal.CsrfTokens.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Login, session and logout through the full filter chain, with sessions in
 * Postgres. Not @Transactional: a staff login rolls back its own rate-limit
 * increment on success, which only shows with real commits.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, OtpServiceTest.TestBeans.class})
class AuthApiTest {

    private static final String PASSWORD = "correct horse battery";
    // Mid-hour and on a 15-minute boundary: no window boundary falls inside a test.
    private static final Instant START = Instant.parse("2026-10-20T06:00:00Z");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AppUserRepository users;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    StaffLoginService staffLogin;

    @Autowired
    FindByIndexNameSessionRepository<? extends Session> sessions;

    @Autowired
    OtpServiceTest.MutableClock clock;

    @Autowired
    OtpServiceTest.CapturingOtpSender sender;

    @BeforeEach
    void reset() {
        jdbc.sql("DELETE FROM spring_session").update();
        jdbc.sql("DELETE FROM rate_limit").update();
        jdbc.sql("DELETE FROM otp_challenge").update();
        jdbc.sql("DELETE FROM app_user WHERE phone LIKE '+9198766%' OR email LIKE 'auth-test-%'").update();
        clock.set(START);
        sender.lastCode.clear();
    }

    static String phone(int n) {
        return "+9198766%05d".formatted(n);
    }

    AppUser staff(String email) {
        return users.save(AppUser.staff("Test Staff", email, encoder.encode(PASSWORD)));
    }

    static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    ResultActions sendOtp(String phone) throws Exception {
        return mvc.perform(json(post("/api/v1/auth/otp/send"), "{\"phone\": \"%s\"}".formatted(phone)).with(csrfToken()));
    }

    ResultActions verifyOtp(String phone, String code) throws Exception {
        return mvc.perform(json(post("/api/v1/auth/otp/verify"),
                "{\"phone\": \"%s\", \"code\": \"%s\"}".formatted(phone, code)).with(csrfToken()));
    }

    ResultActions staffLogin(String email, String password, Cookie... cookies) throws Exception {
        var request = json(post("/api/v1/auth/staff/login"),
                "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)).with(csrfToken());
        if (cookies.length > 0) {
            request.cookie(cookies);
        }
        return mvc.perform(request);
    }

    ResultActions me(Cookie session) throws Exception {
        return mvc.perform(get("/api/v1/auth/me").cookie(session));
    }

    static Cookie sessionCookie(ResultActions result) {
        Cookie cookie = result.andReturn().getResponse().getCookie("SESSION");
        assertThat(cookie).as("SESSION cookie").isNotNull();
        return cookie;
    }

    /** Logs a citizen in by OTP and returns their session cookie. */
    Cookie citizenSession(String phone) throws Exception {
        sendOtp(phone).andExpect(status().isNoContent());
        return sessionCookie(verifyOtp(phone, sender.lastCode.get(phone)).andExpect(status().isOk()));
    }

    // ---------------------------------------------------------------- citizen OTP

    @Test
    void otpLoginEstablishesASessionThatAuthenticatesLaterRequests() throws Exception {
        sendOtp(phone(1)).andExpect(status().isNoContent());

        ResultActions verified = verifyOtp(phone(1), sender.lastCode.get(phone(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("CITIZEN"));
        String userId = JsonPath.read(verified.andReturn().getResponse().getContentAsString(), "$.id");
        assertThat(users.findByPhone(phone(1)).orElseThrow().getId().toString()).isEqualTo(userId);

        me(sessionCookie(verified))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.type").value("CITIZEN"));
    }

    @Test
    void wrongOtpIs401ProblemAndNoSession() throws Exception {
        sendOtp(phone(2));
        String wrong = sender.lastCode.get(phone(2)).equals("000000") ? "000001" : "000000";

        MvcResult result = verifyOtp(phone(2), wrong)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn();
        assertThat(result.getResponse().getCookie("SESSION")).isNull();
    }

    @Test
    void invalidPhoneIs400Problem() throws Exception {
        sendOtp("9876600001")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void resendWithinCooldownIs429WithRetryAfter() throws Exception {
        sendOtp(phone(3)).andExpect(status().isNoContent());
        sendOtp(phone(3))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void requestIpIsTheConnectionAddressNotAForwardedHeader() throws Exception {
        mvc.perform(json(post("/api/v1/auth/otp/send"), "{\"phone\": \"%s\"}".formatted(phone(4)))
                        .with(csrfToken())
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.9");
                            return request;
                        })
                        .header("X-Forwarded-For", "198.51.100.1"))
                .andExpect(status().isNoContent());

        String stored = jdbc.sql("SELECT host(request_ip) FROM otp_challenge WHERE phone = :phone")
                .param("phone", phone(4)).query(String.class).single();
        assertThat(stored).isEqualTo("203.0.113.9");
    }

    // ---------------------------------------------------------------- staff password

    @Test
    void staffLoginEstablishesASession() throws Exception {
        AppUser admin = staff("auth-test-1@example.test");

        ResultActions login = staffLogin("auth-test-1@example.test", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(admin.getId().toString()))
                .andExpect(jsonPath("$.type").value("STAFF"));

        me(sessionCookie(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(admin.getId().toString()));
    }

    @Test
    void everyStaffLoginFailureLooksTheSame() throws Exception {
        staff("auth-test-2@example.test");
        AppUser locked = staff("auth-test-3@example.test");
        locked.setStatus(UserStatus.LOCKED);
        users.save(locked);
        AppUser citizenWithEmail = AppUser.citizen(phone(5), null);
        citizenWithEmail.setEmail("auth-test-4@example.test");
        users.save(citizenWithEmail);

        String wrongPassword = failedLoginBody("auth-test-2@example.test", "wrong");
        assertThat(failedLoginBody("auth-test-nobody@example.test", PASSWORD)).isEqualTo(wrongPassword);
        assertThat(failedLoginBody("auth-test-3@example.test", PASSWORD))
                .as("a locked account with the right password").isEqualTo(wrongPassword);
        assertThat(failedLoginBody("auth-test-4@example.test", PASSWORD))
                .as("a citizen's email").isEqualTo(wrongPassword);
    }

    String failedLoginBody(String email, String password) throws Exception {
        MvcResult result = staffLogin(email, password)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn();
        assertThat(result.getResponse().getCookie("SESSION")).isNull();
        return result.getResponse().getContentAsString();
    }

    @Test
    void emailMatchesWhateverCaseIsTyped() throws Exception {
        AppUser admin = staff("Auth-Test-15@Example.test");

        staffLogin("AUTH-TEST-15@EXAMPLE.TEST", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(admin.getId().toString()));
    }

    @Test
    void caseVariantsOfAnEmailShareOneFailureCounter() throws Exception {
        staff("auth-test-16@example.test");
        for (int i = 0; i < StaffLoginService.MAX_FAILURES; i++) {
            String variant = i % 2 == 0 ? "AUTH-TEST-16@example.test" : "auth-test-16@EXAMPLE.test";
            staffLogin(variant, "wrong").andExpect(status().isUnauthorized());
        }
        staffLogin("auth-test-16@example.test", PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void fiveFailuresBlockTheEmailEvenForTheRightPassword() throws Exception {
        staff("auth-test-5@example.test");
        for (int i = 0; i < StaffLoginService.MAX_FAILURES; i++) {
            staffLogin("auth-test-5@example.test", "wrong").andExpect(status().isUnauthorized());
        }

        staffLogin("auth-test-5@example.test", PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "900"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void unknownEmailIsLimitedLikeAKnownOne() throws Exception {
        for (int i = 0; i < StaffLoginService.MAX_FAILURES; i++) {
            staffLogin("auth-test-nobody@example.test", "wrong").andExpect(status().isUnauthorized());
        }
        staffLogin("auth-test-nobody@example.test", "wrong").andExpect(status().isTooManyRequests());
    }

    @Test
    void successfulLoginDoesNotCountAsAFailure() throws Exception {
        staff("auth-test-6@example.test");
        for (int i = 0; i < StaffLoginService.MAX_FAILURES - 1; i++) {
            staffLogin("auth-test-6@example.test", "wrong").andExpect(status().isUnauthorized());
        }
        staffLogin("auth-test-6@example.test", PASSWORD).andExpect(status().isOk());

        // Fifth failure is still allowed; only then is the email blocked.
        staffLogin("auth-test-6@example.test", "wrong").andExpect(status().isUnauthorized());
        staffLogin("auth-test-6@example.test", PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void limitLiftsWhenTheWindowEnds() throws Exception {
        staff("auth-test-7@example.test");
        for (int i = 0; i < StaffLoginService.MAX_FAILURES; i++) {
            staffLogin("auth-test-7@example.test", "wrong");
        }
        staffLogin("auth-test-7@example.test", PASSWORD).andExpect(status().isTooManyRequests());

        clock.advance(StaffLoginService.FAILURE_WINDOW);
        staffLogin("auth-test-7@example.test", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void concurrentFailuresCannotExceedTheCap() throws Exception {
        staff("auth-test-8@example.test");
        int threads = 10;
        CyclicBarrier start = new CyclicBarrier(threads);
        List<Future<StaffLoginService.Result>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return staffLogin.login("auth-test-8@example.test", "wrong");
                }));
            }
        }
        long failed = 0;
        for (Future<StaffLoginService.Result> result : results) {
            if (result.get() instanceof StaffLoginService.Result.Failed) {
                failed++;
            }
        }
        assertThat(failed).isEqualTo(StaffLoginService.MAX_FAILURES);
    }

    // ---------------------------------------------------------------- session

    @Test
    void cookiesAreHttpOnlySameSiteLaxAndSecure() throws Exception {
        staff("auth-test-9@example.test");
        MvcResult login = staffLogin("auth-test-9@example.test", PASSWORD).andReturn();

        // Cookie objects, not the Set-Cookie text: the mock response drops SameSite
        // from a plain servlet Cookie when rendering the header, though Tomcat keeps it.
        Cookie session = login.getResponse().getCookie("SESSION");
        assertThat(session.isHttpOnly()).isTrue();
        assertThat(session.getSecure()).isTrue();
        assertThat(session.getAttribute("SameSite")).isEqualTo("Lax");

        Cookie xsrf = login.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf.isHttpOnly()).as("Angular must be able to read it").isFalse();
        assertThat(xsrf.getSecure()).isTrue();
        assertThat(xsrf.getAttribute("SameSite")).isEqualTo("Lax");
    }

    @Test
    void sessionHoldsTheUserIdAndTypeButNoPermissions() throws Exception {
        AppUser admin = staff("auth-test-10@example.test");
        staffLogin("auth-test-10@example.test", PASSWORD).andExpect(status().isOk());

        // Found by principal name, which is the user id.
        Map<String, ? extends Session> stored = sessions.findByPrincipalName(admin.getId().toString());
        assertThat(stored).hasSize(1);
        SecurityContext context = stored.values().iterator().next()
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context.getAuthentication().getPrincipal()).isEqualTo(new CurrentUser(admin.getId(), UserType.STAFF));
        assertThat(context.getAuthentication().getAuthorities()).isEmpty();
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        Cookie session = citizenSession(phone(6));
        UUID citizenId = users.findByPhone(phone(6)).orElseThrow().getId();

        mvc.perform(post("/api/v1/auth/logout").cookie(session).with(csrfToken()))
                .andExpect(status().isNoContent());

        me(session).andExpect(status().isUnauthorized());
        assertThat(sessions.findByPrincipalName(citizenId.toString())).isEmpty();
    }

    @Test
    void loginReplacesAnExistingSessionRatherThanReusingIt() throws Exception {
        Cookie citizen = citizenSession(phone(7));
        AppUser admin = staff("auth-test-11@example.test");

        Cookie staffSession = sessionCookie(staffLogin("auth-test-11@example.test", PASSWORD, citizen)
                .andExpect(status().isOk()));

        assertThat(staffSession.getValue()).isNotEqualTo(citizen.getValue());
        me(citizen).andExpect(status().isUnauthorized());
        me(staffSession).andExpect(jsonPath("$.id").value(admin.getId().toString()));
    }

    // ---------------------------------------------------------------- CSRF

    @Test
    void loginWithoutCsrfTokenIs403() throws Exception {
        staff("auth-test-12@example.test");
        mvc.perform(json(post("/api/v1/auth/staff/login"),
                        "{\"email\": \"auth-test-12@example.test\", \"password\": \"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    /** What Angular does: read XSRF-TOKEN from any response, echo it as X-XSRF-TOKEN. */
    @Test
    void csrfCookieFromAnyResponseIsAcceptedAsHeaderAndRotatesOnLogin() throws Exception {
        staff("auth-test-13@example.test");
        Cookie token = mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(token).as("XSRF-TOKEN cookie on an unauthenticated response").isNotNull();

        MvcResult login = mvc.perform(json(post("/api/v1/auth/staff/login"),
                                "{\"email\": \"auth-test-13@example.test\", \"password\": \"%s\"}".formatted(PASSWORD))
                        .cookie(token)
                        .header("X-XSRF-TOKEN", token.getValue()))
                .andExpect(status().isOk())
                .andReturn();

        Cookie rotated = login.getResponse().getCookie("XSRF-TOKEN");
        assertThat(rotated).isNotNull();
        assertThat(rotated.getValue()).isNotEqualTo(token.getValue());
    }
}
