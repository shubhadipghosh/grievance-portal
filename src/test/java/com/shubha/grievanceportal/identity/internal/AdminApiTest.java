package com.shubha.grievanceportal.identity.internal;

import com.jayway.jsonpath.JsonPath;
import com.shubha.grievanceportal.TestcontainersConfiguration;
import com.shubha.grievanceportal.identity.AppUser;
import com.shubha.grievanceportal.identity.CurrentUser;
import com.shubha.grievanceportal.organization.Department;
import com.shubha.grievanceportal.organization.Ward;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.shubha.grievanceportal.CsrfTokens.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/v1/admin: staff accounts, status, grants. Full stack against Postgres,
 * each test rolled back. The first-login password flow is in AuthApiTest,
 * which needs real sessions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class AdminApiTest {

    static final UUID SYSTEM_USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    MockMvc mvc;

    @Autowired
    EntityManager em;

    @Autowired
    PasswordEncoder encoder;

    Department sanitation;
    Ward w12;
    AppUser superAdmin;
    AppUser deptAdmin;
    AppUser officer;
    AppUser citizen;

    @BeforeEach
    void fixtures() {
        sanitation = persist(new Department("TEST_SANITATION", "Sanitation"));
        w12 = persist(new Ward("W12", "Ward 12"));
        superAdmin = persist(AppUser.staff("Municipal Admin", "admin-test-root@example.test", "{bcrypt}x"));
        deptAdmin = persist(AppUser.staff("Sanitation Admin", "admin-test-dept@example.test", "{bcrypt}x"));
        officer = persist(AppUser.staff("Ravi Sen", "admin-test-ravi@example.test", "{bcrypt}x"));
        citizen = persist(AppUser.citizen("+919800000901", "Asha Das"));
        em.flush();
        grant(superAdmin, "SUPER_ADMIN", null, null);
        grant(deptAdmin, "DEPT_ADMIN", sanitation, null);
    }

    <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    void grant(AppUser user, String roleCode, Department department, Ward ward) {
        em.createNativeQuery("""
                        insert into user_role_grant (user_id, role_id, department_id, ward_id)
                        select ?1, r.id, ?2, ?3 from role r where r.code = ?4""")
                .setParameter(1, user.getId())
                .setParameter(2, department == null ? null : department.getId())
                .setParameter(3, ward == null ? null : ward.getId())
                .setParameter(4, roleCode)
                .executeUpdate();
    }

    static RequestPostProcessor as(AppUser user) {
        var principal = new CurrentUser(user.getId(), user.getUserType());
        var signedIn = authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        return request -> csrfToken().postProcessRequest(signedIn.postProcessRequest(request));
    }

    ResultActions postJson(String path, AppUser actor, String json) throws Exception {
        return mvc.perform(post(path).with(as(actor)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    ResultActions grantRole(AppUser actor, UUID userId, String json) throws Exception {
        return postJson("/api/v1/admin/users/" + userId + "/grants", actor, json);
    }

    String officerGrantJson() {
        return """
                {"roleCode": "FIELD_OFFICER", "departmentId": "%s", "wardId": "%s"}"""
                .formatted(sanitation.getId(), w12.getId());
    }

    /** Grants FIELD_OFFICER to the officer and returns the grant id. */
    String grantOfficer() throws Exception {
        String body = grantRole(superAdmin, officer.getId(), officerGrantJson())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    ResultActions revoke(AppUser actor, UUID userId, String grantId, String json) throws Exception {
        return postJson("/api/v1/admin/users/" + userId + "/grants/" + grantId + "/revoke", actor, json);
    }

    static void expectProblem(ResultActions result, int status) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // ---------------------------------------------------------------- create staff

    @Test
    void createStaffReturnsAGeneratedPasswordOnce() throws Exception {
        String body = postJson("/api/v1/admin/users", superAdmin, """
                {"fullName": " Mita Paul ", "email": "Admin-Test-Mita@Example.test"}""")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/admin/users/")))
                .andExpect(jsonPath("$.user.fullName").value("Mita Paul"))
                .andExpect(jsonPath("$.user.email").value("admin-test-mita@example.test"))
                .andExpect(jsonPath("$.user.type").value("STAFF"))
                .andExpect(jsonPath("$.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andExpect(jsonPath("$.user.createdAt").exists())
                .andExpect(jsonPath("$.user.grants").isEmpty())
                .andReturn().getResponse().getContentAsString();

        String password = JsonPath.read(body, "$.temporaryPassword");
        assertThat(password).hasSize(16).matches("[A-Za-z2-9]+");
        UUID id = UUID.fromString(JsonPath.read(body, "$.user.id"));
        AppUser created = em.find(AppUser.class, id);
        assertThat(encoder.matches(password, created.getPasswordHash())).isTrue();

        // Shown once: reading the account back never includes it.
        mvc.perform(get("/api/v1/admin/users/{id}", id).with(as(superAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist());
    }

    @Test
    void createStaffIsAuditedAsTheAccountsFirstStatus() throws Exception {
        String body = postJson("/api/v1/admin/users", superAdmin, """
                {"fullName": "Mita Paul", "email": "admin-test-mita@example.test"}""")
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(body, "$.user.id"));

        Object[] row = (Object[]) em.createNativeQuery("""
                        select from_status, to_status, actor_id, reason from user_status_history where user_id = ?1""")
                .setParameter(1, id).getSingleResult();
        assertThat(row).containsExactly(null, "ACTIVE", superAdmin.getId(), "Staff account created");
    }

    @Test
    void duplicateEmailIs409IgnoringCase() throws Exception {
        expectProblem(postJson("/api/v1/admin/users", superAdmin, """
                {"fullName": "Another Ravi", "email": "ADMIN-TEST-RAVI@example.test"}"""), 409);
    }

    @Test
    void invalidEmailIs400() throws Exception {
        expectProblem(postJson("/api/v1/admin/users", superAdmin, """
                {"fullName": "Mita Paul", "email": "not an email"}"""), 400);
    }

    // ---------------------------------------------------------------- who may

    @Test
    void deptAdminMayNotAdminister() throws Exception {
        expectProblem(mvc.perform(get("/api/v1/admin/users").param("type", "STAFF").with(as(deptAdmin))), 403);
        expectProblem(mvc.perform(get("/api/v1/admin/users/{id}", officer.getId()).with(as(deptAdmin))), 403);
        expectProblem(mvc.perform(get("/api/v1/admin/roles").with(as(deptAdmin))), 403);
        expectProblem(postJson("/api/v1/admin/users", deptAdmin, """
                {"fullName": "Mita Paul", "email": "admin-test-mita@example.test"}"""), 403);
        expectProblem(grantRole(deptAdmin, officer.getId(), officerGrantJson()), 403);
        expectProblem(postJson("/api/v1/admin/users/" + officer.getId() + "/status", deptAdmin, """
                {"status": "LOCKED", "reason": "left"}"""), 403);
    }

    @Test
    void unauthorisedCallerLearnsNothingAboutTheTarget() throws Exception {
        // 403, not 404, for a user that does not exist.
        expectProblem(mvc.perform(get("/api/v1/admin/users/{id}", UUID.randomUUID()).with(as(deptAdmin))), 403);
    }

    @Test
    void citizenMayNotAdminister() throws Exception {
        expectProblem(mvc.perform(get("/api/v1/admin/users").param("type", "STAFF").with(as(citizen))), 403);
    }

    // ---------------------------------------------------------------- list and detail

    @Test
    void listIsByTypeAndSearchesNameEmailAndPhone() throws Exception {
        mvc.perform(get("/api/v1/admin/users").param("type", "STAFF").param("q", "RAVI").with(as(superAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(officer.getId().toString()));

        mvc.perform(get("/api/v1/admin/users").param("type", "STAFF").param("q", "admin-test-dept@")
                        .with(as(superAdmin)))
                .andExpect(jsonPath("$.content[0].id").value(deptAdmin.getId().toString()));

        mvc.perform(get("/api/v1/admin/users").param("type", "CITIZEN").param("q", "9800000901")
                        .with(as(superAdmin)))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].phone").value("+919800000901"));

        // Wildcards in the query are literal.
        mvc.perform(get("/api/v1/admin/users").param("type", "STAFF").param("q", "%").with(as(superAdmin)))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void listIsSortedByNameWhateverSortIsAsked() throws Exception {
        String body = mvc.perform(get("/api/v1/admin/users").param("type", "STAFF").param("q", "admin-test-")
                        .param("sort", "passwordHash,desc").with(as(superAdmin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(body, "$.content[*].fullName"))
                .containsExactly("Municipal Admin", "Ravi Sen", "Sanitation Admin");
    }

    @Test
    void systemUserIsNotAdministered() throws Exception {
        expectProblem(mvc.perform(get("/api/v1/admin/users/{id}", SYSTEM_USER).with(as(superAdmin))), 404);
    }

    @Test
    void rolesListLeavesOutSystem() throws Exception {
        String body = mvc.perform(get("/api/v1/admin/roles").with(as(superAdmin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(body, "$[*].code"))
                .contains("SUPER_ADMIN", "DEPT_ADMIN", "FIELD_OFFICER", "AUDITOR")
                .doesNotContain("SYSTEM");
    }

    // ---------------------------------------------------------------- grants

    @Test
    void grantNamesTheScopeAndTheGranter() throws Exception {
        grantRole(superAdmin, officer.getId(), officerGrantJson())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roleCode").value("FIELD_OFFICER"))
                .andExpect(jsonPath("$.departmentName").value("Sanitation"))
                .andExpect(jsonPath("$.wardName").value("Ward 12"))
                .andExpect(jsonPath("$.grantedByName").value("Municipal Admin"))
                .andExpect(jsonPath("$.grantedAt").exists())
                .andExpect(jsonPath("$.revokedAt").doesNotExist());
    }

    @Test
    void grantTakesEffectAtOnce() throws Exception {
        String departments = "/api/v1/departments";
        mvc.perform(get(departments).with(as(officer))).andExpect(jsonPath("$").isEmpty());
        grantOfficer();
        mvc.perform(get(departments).with(as(officer)))
                .andExpect(jsonPath("$[0].name").value("Sanitation"));
    }

    @Test
    void unscopedGrantHasNoScopeNames() throws Exception {
        grantRole(superAdmin, officer.getId(), "{\"roleCode\": \"AUDITOR\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.departmentId").doesNotExist())
                .andExpect(jsonPath("$.wardName").doesNotExist());
    }

    @Test
    void sameRoleInSameScopeTwiceIs409() throws Exception {
        grantOfficer();
        expectProblem(grantRole(superAdmin, officer.getId(), officerGrantJson()), 409);
    }

    @Test
    void grantToCitizenIs422() throws Exception {
        expectProblem(grantRole(superAdmin, citizen.getId(), officerGrantJson()), 422);
    }

    @Test
    void systemRoleCannotBeGranted() throws Exception {
        expectProblem(grantRole(superAdmin, officer.getId(), "{\"roleCode\": \"SYSTEM\"}"), 422);
    }

    @Test
    void unknownRoleOrScopeIs422() throws Exception {
        expectProblem(grantRole(superAdmin, officer.getId(), "{\"roleCode\": \"MAYOR\"}"), 422);
        expectProblem(grantRole(superAdmin, officer.getId(), """
                {"roleCode": "FIELD_OFFICER", "departmentId": "%s"}""".formatted(UUID.randomUUID())), 422);
        expectProblem(grantRole(superAdmin, officer.getId(), """
                {"roleCode": "FIELD_OFFICER", "wardId": "%s"}""".formatted(UUID.randomUUID())), 422);
    }

    @Test
    void grantToUnknownUserIs404() throws Exception {
        expectProblem(grantRole(superAdmin, UUID.randomUUID(), officerGrantJson()), 404);
    }

    @Test
    void adminCannotChangeTheirOwnGrants() throws Exception {
        expectProblem(grantRole(superAdmin, superAdmin.getId(), "{\"roleCode\": \"AUDITOR\"}"), 403);
        String own = JsonPath.read(mvc.perform(get("/api/v1/admin/users/{id}", superAdmin.getId())
                        .with(as(superAdmin))).andReturn().getResponse().getContentAsString(), "$.grants[0].id");
        expectProblem(revoke(superAdmin, superAdmin.getId(), own, "{\"reason\": \"oops\"}"), 403);
    }

    // ---------------------------------------------------------------- revoke

    @Test
    void revokeRecordsWhoAndWhyAndKeepsTheGrantInHistory() throws Exception {
        String grantId = grantOfficer();

        revoke(superAdmin, officer.getId(), grantId, "{\"reason\": \" Moved to Ward 14 \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revokedAt").exists())
                .andExpect(jsonPath("$.revokedByName").value("Municipal Admin"))
                .andExpect(jsonPath("$.revokeReason").value("Moved to Ward 14"));

        mvc.perform(get("/api/v1/admin/users/{id}", officer.getId()).with(as(superAdmin)))
                .andExpect(jsonPath("$.grants.length()").value(1))
                .andExpect(jsonPath("$.grants[0].id").value(grantId))
                .andExpect(jsonPath("$.grants[0].revokeReason").value("Moved to Ward 14"));
        mvc.perform(get("/api/v1/departments").with(as(officer))).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void revokeTwiceIs409() throws Exception {
        String grantId = grantOfficer();
        revoke(superAdmin, officer.getId(), grantId, "{\"reason\": \"moved\"}");
        expectProblem(revoke(superAdmin, officer.getId(), grantId, "{\"reason\": \"again\"}"), 409);
    }

    @Test
    void revokeNeedsAReason() throws Exception {
        String grantId = grantOfficer();
        expectProblem(revoke(superAdmin, officer.getId(), grantId, "{\"reason\": \" \"}"), 400);
    }

    @Test
    void revokeAnotherUsersGrantIs404() throws Exception {
        String grantId = grantOfficer();
        expectProblem(revoke(superAdmin, deptAdmin.getId(), grantId, "{\"reason\": \"wrong user\"}"), 404);
    }

    @Test
    void systemGrantCannotBeRevoked() throws Exception {
        String systemGrant = em.createNativeQuery("select id from user_role_grant where user_id = ?1")
                .setParameter(1, SYSTEM_USER).getSingleResult().toString();
        expectProblem(revoke(superAdmin, SYSTEM_USER, systemGrant, "{\"reason\": \"tidy up\"}"), 404);
    }

    // ---------------------------------------------------------------- status

    @Test
    void statusChangeGoesThroughTheAuditedService() throws Exception {
        postJson("/api/v1/admin/users/" + officer.getId() + "/status", superAdmin, """
                {"status": "LOCKED", "reason": "Left the department"}""")
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/admin/users/{id}", officer.getId()).with(as(superAdmin)))
                .andExpect(jsonPath("$.status").value("LOCKED"));
        Object reason = em.createNativeQuery("select reason from user_status_history where user_id = ?1")
                .setParameter(1, officer.getId()).getSingleResult();
        assertThat(reason).isEqualTo("Left the department");
    }

    @Test
    void statusChangeNeedsAReason() throws Exception {
        expectProblem(postJson("/api/v1/admin/users/" + officer.getId() + "/status", superAdmin, """
                {"status": "LOCKED"}"""), 400);
    }
}
