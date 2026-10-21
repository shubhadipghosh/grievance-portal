package com.shubha.grievanceportal.grievance.internal;

import com.jayway.jsonpath.JsonPath;
import com.shubha.grievanceportal.TestcontainersConfiguration;
import com.shubha.grievanceportal.identity.AppUser;
import com.shubha.grievanceportal.identity.CurrentUser;
import com.shubha.grievanceportal.organization.Category;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.shubha.grievanceportal.CsrfTokens.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full stack against Postgres: HTTP, validation, services, error mapping.
 * Each test runs in a transaction that is rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class GrievanceApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    EntityManager em;

    Department sanitation;
    Ward w12;
    Category garbage;
    AppUser citizen;
    AppUser admin;
    AppUser officer;

    @BeforeEach
    void fixtures() {
        sanitation = persist(new Department("TEST_SANITATION", "Sanitation"));
        w12 = persist(new Ward("W12", "Ward 12"));
        garbage = persist(new Category("GARBAGE", null, sanitation, 48));
        citizen = persist(AppUser.citizen("+919800000301", "Asha Das"));
        admin = persist(AppUser.staff("Sanitation Admin", null, "{bcrypt}x"));
        officer = persist(AppUser.staff("Ravi Sen", null, "{bcrypt}x"));
        em.flush();
        grant(admin, "DEPT_ADMIN", null);
        grant(officer, "FIELD_OFFICER", w12);
    }

    /**
     * Signed in as the user, with a valid CSRF token. The principal is what a
     * login stores in the session; real login is covered by AuthApiTest.
     */
    static RequestPostProcessor as(AppUser user) {
        var principal = new CurrentUser(user.getId(), user.getUserType());
        var signedIn = authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        return request -> csrfToken().postProcessRequest(signedIn.postProcessRequest(request));
    }

    <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    void grant(AppUser user, String roleCode, Ward ward) {
        em.createNativeQuery("""
                        insert into user_role_grant (user_id, role_id, department_id, ward_id)
                        select ?1, r.id, ?2, ?3 from role r where r.code = ?4""")
                .setParameter(1, user.getId())
                .setParameter(2, sanitation.getId())
                .setParameter(3, ward == null ? null : ward.getId())
                .setParameter(4, roleCode)
                .executeUpdate();
    }

    // No endpoint for transition 5 yet.
    void forceInProgress(String grievanceId) {
        em.createNativeQuery("update grievance set status = 'IN_PROGRESS', current_assignee_id = ?1 where id = ?2")
                .setParameter(1, officer.getId())
                .setParameter(2, UUID.fromString(grievanceId))
                .executeUpdate();
        em.clear();
    }

    String newGrievanceJson(String title) {
        return """
                {"categoryId": "%s", "wardId": "%s", "title": "%s",
                 "description": "Three days now.", "addressText": "12 Park St",
                 "latitude": 22.5726, "longitude": 88.3639}
                """.formatted(garbage.getId(), w12.getId(), title);
    }

    /** Files a grievance as the citizen and returns its id. */
    String file() throws Exception {
        String body = mvc.perform(post("/api/v1/grievances").with(as(citizen))
                        .contentType(MediaType.APPLICATION_JSON).content(newGrievanceJson("Garbage not collected")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    ResultActions act(String action, String grievanceId, AppUser actor, String json) throws Exception {
        var request = post("/api/v1/grievances/{id}/" + action, grievanceId).with(as(actor));
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(request);
    }

    static void expectProblem(ResultActions result, int status) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status));
    }

    // ---------------------------------------------------------------- happy paths

    @Test
    void fileCreatesASubmittedGrievanceWithTicketNumberAndLocation() throws Exception {
        mvc.perform(post("/api/v1/grievances").with(as(citizen))
                        .contentType(MediaType.APPLICATION_JSON).content(newGrievanceJson("Garbage not collected")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketNo").value(matchesPattern("GRV-\\d{4}-\\d{6}")))
                .andExpect(header().string("Location", matchesPattern("/api/v1/grievances/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.departmentId").value(sanitation.getId().toString()))
                .andExpect(jsonPath("$.submittedAt").exists())
                .andExpect(jsonPath("$.slaDueAt").exists());
    }

    @Test
    void getByTicketNumber() throws Exception {
        String created = mvc.perform(post("/api/v1/grievances").with(as(citizen))
                        .contentType(MediaType.APPLICATION_JSON).content(newGrievanceJson("Garbage not collected")))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        String ticketNo = JsonPath.read(created, "$.ticketNo");

        mvc.perform(get("/api/v1/grievances").param("ticketNo", ticketNo).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.ticketNo").value(ticketNo));
    }

    @Test
    void getById() throws Exception {
        String id = file();

        mvc.perform(get("/api/v1/grievances/{id}", id).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void queueIsPagedAndFilteredByDepartmentAndStatus() throws Exception {
        file();
        file();
        String acknowledged = file();
        act("acknowledge", acknowledged, admin, null).andExpect(status().isOk());

        mvc.perform(get("/api/v1/grievances").with(as(admin))
                        .param("departmentId", sanitation.getId().toString())
                        .param("status", "SUBMITTED")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    @Test
    void queueRowsCarryWardAndAssigneeNames() throws Exception {
        String unassigned = file();
        String assigned = file();
        act("acknowledge", unassigned, admin, null).andExpect(status().isOk());
        act("acknowledge", assigned, admin, null).andExpect(status().isOk());
        act("assign", assigned, admin, "{\"assigneeId\": \"%s\"}".formatted(officer.getId()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/grievances").with(as(admin))
                        .param("departmentId", sanitation.getId().toString())
                        .param("status", "ASSIGNED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(assigned))
                .andExpect(jsonPath("$.content[0].wardName").value("Ward 12"))
                .andExpect(jsonPath("$.content[0].assigneeName").value("Ravi Sen"));
        mvc.perform(get("/api/v1/grievances").with(as(admin))
                        .param("departmentId", sanitation.getId().toString())
                        .param("status", "ACKNOWLEDGED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(unassigned))
                .andExpect(jsonPath("$.content[0].wardName").value("Ward 12"))
                .andExpect(jsonPath("$.content[0].assigneeName").value(nullValue()));
    }

    @Test
    void acknowledge() throws Exception {
        act("acknowledge", file(), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
    }

    @Test
    void assign() throws Exception {
        String id = file();
        act("acknowledge", id, admin, null);

        act("assign", id, admin, "{\"assigneeId\": \"%s\"}".formatted(officer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.currentAssigneeId").value(officer.getId().toString()));
    }

    @Test
    void hold() throws Exception {
        String id = file();
        forceInProgress(id);

        act("hold", id, admin, """
                {"reasonType": "AWAITING_CONTRACTOR", "ownerType": "EXTERNAL",
                 "holdOwnerNote": "Ganguly Contractors", "reason": "waiting on parts"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_HOLD"))
                .andExpect(jsonPath("$.holdReasonType").value("AWAITING_CONTRACTOR"))
                .andExpect(jsonPath("$.holdSince").exists());
    }

    @Test
    void resolve() throws Exception {
        String id = file();
        forceInProgress(id);

        act("resolve", id, officer, "{\"reason\": \"bins cleared\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").exists());
    }

    @Test
    void queueRowsCarryResolvedAtOnlyOnceResolved() throws Exception {
        String resolved = file();
        String working = file();
        forceInProgress(resolved);
        forceInProgress(working);
        String body = act("resolve", resolved, officer, "{\"reason\": \"bins cleared\"}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Instant resolvedAt = Instant.parse(JsonPath.read(body, "$.resolvedAt"));

        String queue = mvc.perform(get("/api/v1/grievances").with(as(admin))
                        .param("departmentId", sanitation.getId().toString())
                        .param("status", "RESOLVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(resolved))
                .andReturn().getResponse().getContentAsString();
        assertThat(Instant.parse(JsonPath.read(queue, "$.content[0].resolvedAt"))).isEqualTo(resolvedAt);

        mvc.perform(get("/api/v1/grievances").with(as(admin))
                        .param("departmentId", sanitation.getId().toString())
                        .param("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(working))
                .andExpect(jsonPath("$.content[0].resolvedAt").value(nullValue()));
    }

    @Test
    void citizenClosesAfterResolution() throws Exception {
        String id = file();
        forceInProgress(id);
        act("resolve", id, officer, "{\"reason\": \"bins cleared\"}");

        act("close", id, citizen, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedAt").exists());
    }

    // ---------------------------------------------------------------- errors, end to end

    @Test
    void unknownTicketIs404() throws Exception {
        expectProblem(mvc.perform(get("/api/v1/grievances").param("ticketNo", "GRV-1999-000000")
                .with(as(admin))), 404);
    }

    @Test
    void unknownIdIs404() throws Exception {
        expectProblem(mvc.perform(get("/api/v1/grievances/{id}", UUID.randomUUID()).with(as(admin))), 404);
    }

    @Test
    void descriptionOver5000CharsIs400() throws Exception {
        String body = newGrievanceJson("Garbage not collected")
                .replace("Three days now.", "x".repeat(5001));
        expectProblem(mvc.perform(post("/api/v1/grievances").with(as(citizen))
                .contentType(MediaType.APPLICATION_JSON).content(body)), 400);
    }

    @Test
    void invalidBodyIs400() throws Exception {
        expectProblem(mvc.perform(post("/api/v1/grievances").with(as(citizen))
                .contentType(MediaType.APPLICATION_JSON).content(newGrievanceJson(""))), 400);
    }

    @Test
    void unauthenticatedIs401() throws Exception {
        expectProblem(mvc.perform(post("/api/v1/grievances").with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON).content(newGrievanceJson("x"))), 401);
    }

    @Test
    void staffCannotFileAndGets403() throws Exception {
        expectProblem(mvc.perform(post("/api/v1/grievances").with(as(admin))
                .contentType(MediaType.APPLICATION_JSON).content(newGrievanceJson("x"))), 403);
    }

    @Test
    void parentCategoryIs422() throws Exception {
        persist(new Category("OVERFLOWING_BIN", garbage, sanitation, 24));
        em.flush();
        ResultActions result = mvc.perform(post("/api/v1/grievances").with(as(citizen))
                .contentType(MediaType.APPLICATION_JSON).content(newGrievanceJson("x")));
        expectProblem(result, 422);
        result.andExpect(jsonPath("$.detail").value(containsString("has sub-categories")));
    }

    @Test
    void parentWhoseSubCategoriesAreAllInactiveCanBeFiledAgainst() throws Exception {
        persist(new Category("OVERFLOWING_BIN", garbage, sanitation, 24)).setActive(false);
        em.flush();
        file();
    }

    @Test
    void unknownCategoryIs422() throws Exception {
        String json = newGrievanceJson("x").replace(garbage.getId().toString(), UUID.randomUUID().toString());
        expectProblem(mvc.perform(post("/api/v1/grievances").with(as(citizen))
                .contentType(MediaType.APPLICATION_JSON).content(json)), 422);
    }

    @Test
    void actorWithoutPermissionIs403() throws Exception {
        // Field officers cannot triage.
        expectProblem(act("acknowledge", file(), officer, null), 403);
    }

    @Test
    void illegalTransitionIs409() throws Exception {
        expectProblem(act("resolve", file(), admin, "{\"reason\": \"done\"}"), 409);
    }

    @Test
    void missingReasonIs400() throws Exception {
        String id = file();
        forceInProgress(id);
        expectProblem(act("resolve", id, officer, null), 400);
    }

    @Test
    void ineligibleAssigneeIs422() throws Exception {
        String id = file();
        act("acknowledge", id, admin, null);
        expectProblem(act("assign", id, admin, "{\"assigneeId\": \"%s\"}".formatted(citizen.getId())), 422);
    }

    @Test
    void invalidHoldDetailsAre422() throws Exception {
        String id = file();
        forceInProgress(id);
        expectProblem(act("hold", id, admin, """
                {"reasonType": "AWAITING_CONTRACTOR", "ownerType": "EXTERNAL", "reason": "waiting"}"""), 422);
    }
}
