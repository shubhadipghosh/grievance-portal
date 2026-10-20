package com.shubha.grievanceportal.identity.internal;

import com.shubha.grievanceportal.TestcontainersConfiguration;
import com.shubha.grievanceportal.identity.AppUser;
import com.shubha.grievanceportal.identity.AuthorizationQuery;
import com.shubha.grievanceportal.identity.OtpSendResult;
import com.shubha.grievanceportal.identity.OtpService;
import com.shubha.grievanceportal.identity.OtpVerifyResult;
import com.shubha.grievanceportal.identity.UserStatus;
import com.shubha.grievanceportal.identity.UserStatusService;
import com.shubha.grievanceportal.shared.IllegalTransitionException;
import com.shubha.grievanceportal.shared.NotAuthorizedException;
import com.shubha.grievanceportal.shared.NotFoundException;
import com.shubha.grievanceportal.shared.ReasonRequiredException;
import com.shubha.grievanceportal.shared.SystemActor;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.shubha.grievanceportal.CsrfTokens.csrfToken;
import static com.shubha.grievanceportal.identity.UserStatus.ACTIVE;
import static com.shubha.grievanceportal.identity.UserStatus.DISABLED;
import static com.shubha.grievanceportal.identity.UserStatus.LOCKED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Not @Transactional: "immediately" means the change is visible to the next
 * request once committed, and a successful staff login rolls back its own
 * transaction. History rows are append-only and pin the users they reference,
 * so fixtures are never deleted; unique emails and phones keep tests apart.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, OtpServiceTest.TestBeans.class})
class UserStatusServiceTest {

    private static final String PASSWORD = "correct horse battery";
    private static final String IP = "203.0.113.9";
    // Distinct from the prefixes OtpServiceTest and AuthApiTest delete.
    private static final AtomicInteger PHONES = new AtomicInteger();

    @Autowired
    UserStatusService service;

    @Autowired
    AuthorizationQuery authorizationQuery;

    @Autowired
    StaffLoginService staffLogin;

    @Autowired
    OtpService otp;

    @Autowired
    AppUserRepository users;

    @Autowired
    UserStatusHistoryRepository history;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvc mvc;

    @Autowired
    FindByIndexNameSessionRepository<? extends Session> sessions;

    @Autowired
    OtpServiceTest.MutableClock clock;

    @Autowired
    OtpServiceTest.CapturingOtpSender sender;

    AppUser admin;
    AppUser target;

    @BeforeEach
    void fixtures() {
        admin = staff();
        grant(admin, "SUPER_ADMIN", null);
        target = staff();
        grant(target, "SUPER_ADMIN", null);
    }

    AppUser staff() {
        String email = "status-test-" + UUID.randomUUID() + "@example.test";
        return users.save(AppUser.staff("Test Staff", email, encoder.encode(PASSWORD)));
    }

    AppUser citizen() {
        return users.save(AppUser.citizen("+9198767%05d".formatted(PHONES.incrementAndGet()), null));
    }

    // Null department = unrestricted on that axis.
    void grant(AppUser user, String roleCode, UUID departmentId) {
        jdbc.sql("""
                        insert into user_role_grant (user_id, role_id, department_id)
                        select ?, r.id, ? from role r where r.code = ?""")
                .params(user.getId(), departmentId, roleCode)
                .update();
    }

    UUID department() {
        return jdbc.sql("insert into department (code, name) values (?, 'Test Department') returning id")
                .param("D-" + UUID.randomUUID().toString().substring(0, 8))
                .query(UUID.class).single();
    }

    UserStatus statusOf(AppUser user) {
        return users.findById(user.getId()).orElseThrow().getStatus();
    }

    // ---------------------------------------------------------------- changes

    @Test
    void suspendThenReinstate() {
        service.changeUserStatus(target.getId(), LOCKED, admin.getId(), "Repeated abusive comments");
        assertThat(statusOf(target)).isEqualTo(LOCKED);

        service.changeUserStatus(target.getId(), ACTIVE, admin.getId(), "Appeal upheld");
        assertThat(statusOf(target)).isEqualTo(ACTIVE);
    }

    @Test
    void roundTripWritesOneHistoryRowPerChange() {
        AppUser otherAdmin = staff();
        grant(otherAdmin, "SUPER_ADMIN", null);

        service.changeUserStatus(target.getId(), LOCKED, admin.getId(), "Repeated abusive comments");
        service.changeUserStatus(target.getId(), ACTIVE, otherAdmin.getId(), "Appeal upheld");

        assertThat(history.findByUserIdOrderByOccurredAt(target.getId()))
                .satisfiesExactly(
                        h -> {
                            assertThat(h.getFromStatus()).isEqualTo(ACTIVE);
                            assertThat(h.getToStatus()).isEqualTo(LOCKED);
                            assertThat(h.getActor().getId()).isEqualTo(admin.getId());
                            assertThat(h.getReason()).isEqualTo("Repeated abusive comments");
                            assertThat(h.getOccurredAt()).isNotNull();
                        },
                        h -> {
                            assertThat(h.getFromStatus()).isEqualTo(LOCKED);
                            assertThat(h.getToStatus()).isEqualTo(ACTIVE);
                            assertThat(h.getActor().getId()).isEqualTo(otherAdmin.getId());
                            assertThat(h.getReason()).isEqualTo("Appeal upheld");
                        });
    }

    @Test
    void historyIsAppendOnly() {
        service.changeUserStatus(target.getId(), DISABLED, admin.getId(), "Left the municipality");
        UUID id = history.findByUserIdOrderByOccurredAt(target.getId()).getFirst().getId();

        assertThatThrownBy(() -> jdbc.sql("update user_status_history set reason = 'x' where id = ?")
                .param(id).update())
                .hasStackTraceContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("delete from user_status_history where id = ?")
                .param(id).update())
                .hasStackTraceContaining("append-only");
    }

    // ---------------------------------------------------------------- rejections

    void assertRejected(UUID targetId, UserStatus newStatus, UUID actorId, String reason,
                        Class<? extends RuntimeException> expected) {
        UserStatus before = users.findById(targetId).orElseThrow().getStatus();
        int rowsBefore = history.findByUserIdOrderByOccurredAt(targetId).size();

        assertThatThrownBy(() -> service.changeUserStatus(targetId, newStatus, actorId, reason))
                .isInstanceOf(expected);

        assertThat(users.findById(targetId).orElseThrow().getStatus()).isEqualTo(before);
        assertThat(history.findByUserIdOrderByOccurredAt(targetId)).hasSize(rowsBefore);
    }

    @Test
    void actorWithoutUnscopedUserSuspendIsRejected() {
        // DEPT_ADMIN does not hold USER_SUSPEND, even unscoped.
        AppUser deptAdmin = staff();
        grant(deptAdmin, "DEPT_ADMIN", null);
        // SUPER_ADMIN holds it, but a department-scoped grant does not reach municipality-wide.
        AppUser scopedSuperAdmin = staff();
        grant(scopedSuperAdmin, "SUPER_ADMIN", department());
        AppUser noGrants = staff();
        AppUser citizen = citizen();

        for (AppUser actor : new AppUser[] {deptAdmin, scopedSuperAdmin, noGrants, citizen}) {
            assertRejected(target.getId(), LOCKED, actor.getId(), "Reason", NotAuthorizedException.class);
        }
    }

    @Test
    void suspendedAdminCannotActEvenOnOthers() {
        service.changeUserStatus(target.getId(), LOCKED, admin.getId(), "Compromised account");

        AppUser other = staff();
        assertRejected(other.getId(), LOCKED, target.getId(), "Reason", NotAuthorizedException.class);
    }

    @Test
    void nobodyChangesTheirOwnStatus() {
        assertRejected(admin.getId(), LOCKED, admin.getId(), "Locking myself out", NotAuthorizedException.class);

        service.changeUserStatus(target.getId(), LOCKED, admin.getId(), "Compromised account");
        assertRejected(target.getId(), ACTIVE, target.getId(), "Reinstating myself", NotAuthorizedException.class);
    }

    @Test
    void systemUserStatusNeverChanges() {
        for (UserStatus status : new UserStatus[] {LOCKED, DISABLED}) {
            assertRejected(SystemActor.ID, status, admin.getId(), "Reason", NotAuthorizedException.class);
        }
    }

    @Test
    void blankReasonIsRejected() {
        for (String reason : new String[] {null, "", "   ", "\t\n"}) {
            assertRejected(target.getId(), LOCKED, admin.getId(), reason, ReasonRequiredException.class);
        }
    }

    @Test
    void noOpChangeIsRejected() {
        assertRejected(target.getId(), ACTIVE, admin.getId(), "Reason", IllegalTransitionException.class);
    }

    @Test
    void unknownTargetIsNotFound() {
        assertThatThrownBy(() -> service.changeUserStatus(UUID.randomUUID(), LOCKED, admin.getId(), "Reason"))
                .isInstanceOf(NotFoundException.class);
    }

    // ---------------------------------------------------------------- effect

    @Test
    void suspendedUserFailsAuthorizationImmediatelyOnEveryPath() {
        AppUser citizen = citizen();
        UUID anyDept = UUID.randomUUID();
        UUID anyWard = UUID.randomUUID();
        Set<String> view = Set.of("GRIEVANCE_VIEW");

        assertThat(authorizationQuery.hasPermissionInScope(target.getId(), view, anyDept, anyWard)).isTrue();
        assertThat(authorizationQuery.scopesWithPermission(target.getId(), "GRIEVANCE_VIEW")).isNotEmpty();
        assertThat(staffLogin.login(target.getEmail(), PASSWORD))
                .isInstanceOf(StaffLoginService.Result.Authenticated.class);

        service.changeUserStatus(target.getId(), LOCKED, admin.getId(), "Compromised account");
        service.changeUserStatus(citizen.getId(), LOCKED, admin.getId(), "Fraudulent filings");

        assertThat(authorizationQuery.hasPermissionInScope(target.getId(), view, anyDept, anyWard)).isFalse();
        assertThat(authorizationQuery.scopesWithPermission(target.getId(), "GRIEVANCE_VIEW")).isEmpty();
        assertThat(staffLogin.login(target.getEmail(), PASSWORD)).isInstanceOf(StaffLoginService.Result.Failed.class);
        assertThat(otpLogin(citizen)).isInstanceOf(OtpVerifyResult.Failed.class);

        service.changeUserStatus(target.getId(), ACTIVE, admin.getId(), "Password reset");
        service.changeUserStatus(citizen.getId(), ACTIVE, admin.getId(), "Appeal upheld");

        assertThat(authorizationQuery.hasPermissionInScope(target.getId(), view, anyDept, anyWard)).isTrue();
        assertThat(authorizationQuery.scopesWithPermission(target.getId(), "GRIEVANCE_VIEW")).isNotEmpty();
        assertThat(staffLogin.login(target.getEmail(), PASSWORD))
                .isInstanceOf(StaffLoginService.Result.Authenticated.class);
        assertThat(otpLogin(citizen)).isEqualTo(new OtpVerifyResult.Verified(citizen.getId()));
    }

    @Test
    void suspensionEndsTheTargetsSessionsButNoOneElses() throws Exception {
        Cookie targetSession = staffSession(target);
        Cookie adminSession = staffSession(admin);
        mvc.perform(get("/api/v1/auth/me").cookie(targetSession)).andExpect(status().isOk());

        service.changeUserStatus(target.getId(), LOCKED, admin.getId(), "Compromised account");

        mvc.perform(get("/api/v1/auth/me").cookie(targetSession)).andExpect(status().isUnauthorized());
        assertThat(sessions.findByPrincipalName(target.getId().toString())).isEmpty();
        mvc.perform(get("/api/v1/auth/me").cookie(adminSession)).andExpect(status().isOk());
    }

    Cookie staffSession(AppUser user) throws Exception {
        Cookie session = mvc.perform(post("/api/v1/auth/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(user.getEmail(), PASSWORD))
                        .with(csrfToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("SESSION");
        assertThat(session).as("SESSION cookie").isNotNull();
        return session;
    }

    OtpVerifyResult otpLogin(AppUser citizen) {
        // Past the resend cooldown, so each call can send a fresh code.
        clock.advance(Duration.ofMinutes(2));
        assertThat(otp.sendLoginOtp(citizen.getPhone(), IP, "test-agent")).isEqualTo(new OtpSendResult.Sent());
        return otp.verifyLoginOtp(citizen.getPhone(), sender.lastCode.get(citizen.getPhone()), IP);
    }
}
