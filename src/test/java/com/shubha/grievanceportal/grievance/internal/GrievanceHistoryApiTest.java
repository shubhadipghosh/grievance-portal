package com.shubha.grievanceportal.grievance.internal;

import com.jayway.jsonpath.JsonPath;
import com.shubha.grievanceportal.TestcontainersConfiguration;
import com.shubha.grievanceportal.grievance.GrievanceStatus;
import com.shubha.grievanceportal.grievance.GrievanceTransitionService;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.shubha.grievanceportal.CsrfTokens.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The citizen's own list, and what each viewer sees of the status history
 * (STATE_MACHINE.md, "What citizens see"). Each test is rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class GrievanceHistoryApiTest {

    // Free text written for colleagues. None of it may reach a citizen.
    static final String ACK_NOTE = "Probably a duplicate of last week's";
    static final String ASSIGN_NOTE = "Ravi is closest, send him";
    static final String HOLD_NOTE = "Ganguly's crew is unreliable, chase them daily";
    static final String STAFF_CLOSE_NOTE = "Closed by phone, citizen hard to reach";
    // Written for the citizen.
    static final String RESOLUTION = "Bins emptied and area cleaned";
    static final String REJECTION = "Duplicate of GRV-2026-000001";
    static final String CITIZEN_CLOSE = "Thanks, all clear now";

    @Autowired
    MockMvc mvc;

    @Autowired
    EntityManager em;

    @Autowired
    GrievanceTransitionService transitions;

    Department sanitation;
    Ward w12;
    Category garbage;
    AppUser citizen;
    AppUser otherCitizen;
    AppUser admin;      // DEPT_ADMIN, Sanitation
    AppUser officer;    // FIELD_OFFICER, Sanitation, Ward 12

    @BeforeEach
    void fixtures() {
        sanitation = persist(new Department("TEST_SANITATION", "Sanitation"));
        w12 = persist(new Ward("W12", "Ward 12"));
        garbage = persist(new Category("GARBAGE", null, sanitation, 48));
        citizen = persist(AppUser.citizen("+919800000601", "Asha Das"));
        otherCitizen = persist(AppUser.citizen("+919800000602", "Bimal Roy"));
        admin = persist(AppUser.staff("Sanitation Admin", null, "{bcrypt}x"));
        officer = persist(AppUser.staff("Ravi Sen", null, "{bcrypt}x"));
        em.flush();
        grant(admin, "DEPT_ADMIN", null);
        grant(officer, "FIELD_OFFICER", w12);
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

    static RequestPostProcessor as(AppUser user) {
        var principal = new CurrentUser(user.getId(), user.getUserType());
        var signedIn = authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        return request -> csrfToken().postProcessRequest(signedIn.postProcessRequest(request));
    }

    /** Files as the citizen; returns {id, ticketNo}. */
    Map<String, String> file(AppUser by, String title) throws Exception {
        String body = mvc.perform(post("/api/v1/grievances").with(as(by))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"categoryId": "%s", "wardId": "%s", "title": "%s", "description": "Three days now."}
                                """.formatted(garbage.getId(), w12.getId(), title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Map.of("id", JsonPath.read(body, "$.id"), "ticketNo", JsonPath.read(body, "$.ticketNo"));
    }

    void act(String action, String id, AppUser actor, String json) throws Exception {
        mvc.perform(post("/api/v1/grievances/{id}/" + action, id).with(as(actor))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    /** Filed, acknowledged, assigned, started, held, resumed, resolved: every kind of staff note. */
    String resolvedAfterAHold() throws Exception {
        String id = file(citizen, "Garbage not collected").get("id");
        UUID uuid = UUID.fromString(id);
        act("acknowledge", id, admin, "{\"reason\": \"%s\"}".formatted(ACK_NOTE));
        act("assign", id, admin, "{\"assigneeId\": \"%s\", \"reason\": \"%s\"}".formatted(officer.getId(), ASSIGN_NOTE));
        transitions.transition(uuid, GrievanceStatus.IN_PROGRESS, officer.getId(), null);
        act("hold", id, admin, """
                {"reasonType": "AWAITING_CONTRACTOR", "ownerType": "EXTERNAL",
                 "holdOwnerNote": "Ganguly Contractors", "reason": "%s"}""".formatted(HOLD_NOTE));
        transitions.transition(uuid, GrievanceStatus.IN_PROGRESS, admin.getId(), null);
        act("resolve", id, officer, "{\"reason\": \"%s\"}".formatted(RESOLUTION));
        return id;
    }

    String history(String id, AppUser viewer) throws Exception {
        return mvc.perform(get("/api/v1/grievances/{id}/history", id).with(as(viewer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    static Map<String, Object> entry(String body, String toStatus) {
        List<Map<String, Object>> matches = JsonPath.read(body, "$[?(@.toStatus == '" + toStatus + "')]");
        assertThat(matches).hasSize(1);
        return matches.getFirst();
    }

    // ---------------------------------------------------------------- citizen view

    @Test
    void citizenNeverSeesStaffNotes() throws Exception {
        String body = history(resolvedAfterAHold(), citizen);

        assertThat(body).doesNotContain(ACK_NOTE, ASSIGN_NOTE, HOLD_NOTE, "Ganguly");
    }

    // Rule 1, reversed by the stakeholder on 2026-11-17.
    @Test
    void citizenSeesWhoActed() throws Exception {
        List<String> names = JsonPath.read(history(resolvedAfterAHold(), citizen), "$[*].actorName");
        assertThat(names).containsExactly("Asha Das", "Sanitation Admin", "Sanitation Admin", "Ravi Sen",
                "Sanitation Admin", "Sanitation Admin", "Ravi Sen");
    }

    @Test
    void citizenSeesTheHoldCategoryButNeverTheHoldText() throws Exception {
        Map<String, Object> hold = entry(history(resolvedAfterAHold(), citizen), "ON_HOLD");

        assertThat(hold)
                .containsEntry("holdReasonType", "AWAITING_CONTRACTOR")
                .containsEntry("reason", null)
                .containsEntry("actorType", "STAFF");
    }

    @Test
    void citizenSeesTheResolutionNote() throws Exception {
        assertThat(entry(history(resolvedAfterAHold(), citizen), "RESOLVED")).containsEntry("reason", RESOLUTION);
    }

    @Test
    void citizenSeesTheirOwnClosingNoteAsTheirs() throws Exception {
        String id = resolvedAfterAHold();
        act("close", id, citizen, "{\"reason\": \"%s\"}".formatted(CITIZEN_CLOSE));

        String body = history(id, citizen);
        assertThat(entry(body, "CLOSED"))
                .containsEntry("reason", CITIZEN_CLOSE)
                .containsEntry("actorType", "CITIZEN");
        List<String> actors = JsonPath.read(body, "$[*].actorType");
        assertThat(actors).containsExactly("CITIZEN", "STAFF", "STAFF", "STAFF", "STAFF", "STAFF", "STAFF", "CITIZEN");
    }

    @Test
    void citizenDoesNotSeeAStaffClosingNote() throws Exception {
        String id = resolvedAfterAHold();
        act("close", id, admin, "{\"reason\": \"%s\"}".formatted(STAFF_CLOSE_NOTE));

        String body = history(id, citizen);
        assertThat(body).doesNotContain(STAFF_CLOSE_NOTE);
        assertThat(entry(body, "CLOSED")).containsEntry("reason", null);
    }

    @Test
    void citizenSeesTheRejectionReason() throws Exception {
        String id = file(citizen, "Garbage again").get("id");
        transitions.transition(UUID.fromString(id), GrievanceStatus.REJECTED, admin.getId(), REJECTION);

        assertThat(entry(history(id, citizen), "REJECTED")).containsEntry("reason", REJECTION);
    }

    // ---------------------------------------------------------------- staff view

    @Test
    void staffSeeEveryNoteAndName() throws Exception {
        String body = history(resolvedAfterAHold(), officer);

        assertThat(body).contains(ACK_NOTE, ASSIGN_NOTE, HOLD_NOTE, RESOLUTION, "Ravi Sen", "Sanitation Admin", "Asha Das");
        assertThat(entry(body, "ON_HOLD"))
                .containsEntry("reason", HOLD_NOTE)
                .containsEntry("holdReasonType", "AWAITING_CONTRACTOR")
                .containsEntry("actorName", "Sanitation Admin");
    }

    @Test
    void historyIsOldestFirst() throws Exception {
        List<String> statuses = JsonPath.read(history(resolvedAfterAHold(), officer), "$[*].toStatus");
        assertThat(statuses).containsExactly(
                "SUBMITTED", "ACKNOWLEDGED", "ASSIGNED", "IN_PROGRESS", "ON_HOLD", "IN_PROGRESS", "RESOLVED");
    }

    // ---------------------------------------------------------------- another citizen

    @Test
    void anotherCitizenCannotReachItThroughAnyPath() throws Exception {
        Map<String, String> filed = file(citizen, "Garbage not collected");

        String mine = mvc.perform(get("/api/v1/grievances/mine").with(as(otherCitizen)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(mine).doesNotContain(filed.get("id"));
        mvc.perform(get("/api/v1/grievances/{id}/history", filed.get("id")).with(as(otherCitizen)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/grievances").param("ticketNo", filed.get("ticketNo")).with(as(otherCitizen)))
                .andExpect(status().isNotFound());
    }

    @Test
    void historyOfAnUnknownGrievanceIs404() throws Exception {
        mvc.perform(get("/api/v1/grievances/{id}/history", UUID.randomUUID()).with(as(citizen)))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- mine

    @Test
    void mineListsOwnGrievancesNewestFirstWithoutStaffDetail() throws Exception {
        String first = file(citizen, "First").get("id");
        String second = file(citizen, "Second").get("id");
        file(otherCitizen, "Not mine");

        String body = mvc.perform(get("/api/v1/grievances/mine").with(as(citizen)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(body, "$.content[*].id")).containsExactly(second, first);
        assertThat(JsonPath.<Integer>read(body, "$.page.totalElements")).isEqualTo(2);
        Map<String, Object> row = JsonPath.read(body, "$.content[0]");
        assertThat(row).containsOnlyKeys("id", "ticketNo", "status", "title", "wardName", "submittedAt", "resolvedAt")
                .containsEntry("wardName", "Ward 12")
                .containsEntry("status", "SUBMITTED");
    }

    @Test
    void mineIsPaged() throws Exception {
        for (int i = 0; i < 3; i++) {
            file(citizen, "Grievance " + i);
        }
        mvc.perform(get("/api/v1/grievances/mine").param("size", "2").param("page", "1").with(as(citizen)))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(JsonPath.<List<Object>>read(body, "$.content")).hasSize(1);
                    assertThat(JsonPath.<Integer>read(body, "$.page.totalPages")).isEqualTo(2);
                });
    }
}
