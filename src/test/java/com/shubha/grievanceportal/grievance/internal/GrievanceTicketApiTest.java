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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
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
 * The ticket page endpoints: overview, activity feed, comments, and what each
 * viewer sees of them (STATE_MACHINE.md, "The ticket page"). Each test is rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class GrievanceTicketApiTest {

    // Staff free text. None of it may reach a citizen.
    static final String ASSIGN_NOTE = "Ravi is closest, send him";
    static final String REROUTE_NOTE = "Pipe burst, not garbage";
    static final String INTERNAL_REMARK = "Contractor overbilled last time, check rates";

    @Autowired
    MockMvc mvc;

    @Autowired
    EntityManager em;

    @Autowired
    GrievanceTransitionService transitions;

    Department sanitation;
    Department water;
    Ward w12;
    Category garbage;
    Category pipeBurst;
    AppUser citizen;
    AppUser otherCitizen;
    AppUser admin;          // DEPT_ADMIN, Sanitation
    AppUser waterAdmin;     // DEPT_ADMIN, Water
    AppUser officer;        // FIELD_OFFICER, Sanitation, Ward 12
    AppUser secondOfficer;  // FIELD_OFFICER, Sanitation, Ward 12
    AppUser auditor;        // AUDITOR, Sanitation: views internal, may not comment

    @BeforeEach
    void fixtures() {
        sanitation = persist(new Department("TEST_SANITATION", "Sanitation"));
        water = persist(new Department("TEST_WATER", "Water Supply"));
        w12 = persist(new Ward("W12", "Ward 12"));
        garbage = persist(new Category("TEST_GARBAGE", null, sanitation, 48));
        pipeBurst = persist(new Category("TEST_PIPE", null, water, 24));
        citizen = persist(AppUser.citizen("+919800000701", "Asha Das"));
        otherCitizen = persist(AppUser.citizen("+919800000702", "Bimal Roy"));
        admin = persist(AppUser.staff("Sanitation Admin", null, "{bcrypt}x"));
        waterAdmin = persist(AppUser.staff("Water Admin", null, "{bcrypt}x"));
        officer = persist(AppUser.staff("Ravi Sen", null, "{bcrypt}x"));
        secondOfficer = persist(AppUser.staff("Mita Paul", null, "{bcrypt}x"));
        auditor = persist(AppUser.staff("Audit Office", null, "{bcrypt}x"));
        em.flush();
        translate(garbage, "en", "Garbage not collected");
        translate(garbage, "bn", "আবর্জনা সংগ্রহ হয়নি");
        translate(pipeBurst, "en", "Pipe burst");
        grant(admin, "DEPT_ADMIN", sanitation, null);
        grant(waterAdmin, "DEPT_ADMIN", water, null);
        grant(officer, "FIELD_OFFICER", sanitation, w12);
        grant(secondOfficer, "FIELD_OFFICER", sanitation, w12);
        grant(auditor, "AUDITOR", sanitation, null);
    }

    <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    void translate(Category category, String locale, String name) {
        em.createNativeQuery("insert into category_translation (category_id, locale, name) values (?1, ?2, ?3)")
                .setParameter(1, category.getId())
                .setParameter(2, locale)
                .setParameter(3, name)
                .executeUpdate();
    }

    void grant(AppUser user, String roleCode, Department department, Ward ward) {
        em.createNativeQuery("""
                        insert into user_role_grant (user_id, role_id, department_id, ward_id)
                        select ?1, r.id, ?2, ?3 from role r where r.code = ?4""")
                .setParameter(1, user.getId())
                .setParameter(2, department.getId())
                .setParameter(3, ward == null ? null : ward.getId())
                .setParameter(4, roleCode)
                .executeUpdate();
    }

    static RequestPostProcessor as(AppUser user) {
        var principal = new CurrentUser(user.getId(), user.getUserType());
        var signedIn = authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        return request -> csrfToken().postProcessRequest(signedIn.postProcessRequest(request));
    }

    String file() throws Exception {
        String body = mvc.perform(post("/api/v1/grievances").with(as(citizen))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"categoryId": "%s", "wardId": "%s", "title": "Bins overflowing", "description": "Three days now."}
                                """.formatted(garbage.getId(), w12.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    String assigned() throws Exception {
        String id = file();
        transitions.transition(UUID.fromString(id), GrievanceStatus.ACKNOWLEDGED, admin.getId(), null);
        transitions.assign(UUID.fromString(id), officer.getId(), admin.getId(), ASSIGN_NOTE);
        return id;
    }

    String read(String path, String id, AppUser viewer) throws Exception {
        // As a fresh request would: rows written earlier in this test's
        // transaction are reloaded with their DB defaults (assigned_at etc.).
        em.flush();
        em.clear();
        return mvc.perform(get("/api/v1/grievances/{id}/" + path, id).with(as(viewer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    ResultActions comment(String id, AppUser actor, String json) throws Exception {
        return mvc.perform(post("/api/v1/grievances/{id}/comments", id).with(as(actor))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    static List<Map<String, Object>> ofKind(String activity, String kind) {
        return JsonPath.read(activity, "$[?(@.kind == '" + kind + "')]");
    }

    // ---------------------------------------------------------------- overview

    @Test
    void overviewNamesEverythingTheSidePanelShows() throws Exception {
        String body = read("overview", assigned(), citizen);

        assertThat(JsonPath.<String>read(body, "$.grievance.status")).isEqualTo("ASSIGNED");
        assertThat(JsonPath.<String>read(body, "$.categoryName")).isEqualTo("Garbage not collected");
        assertThat(JsonPath.<String>read(body, "$.departmentName")).isEqualTo("Sanitation");
        assertThat(JsonPath.<String>read(body, "$.wardName")).isEqualTo("Ward 12");
        assertThat(JsonPath.<String>read(body, "$.reporterName")).isEqualTo("Asha Das");
        assertThat(JsonPath.<String>read(body, "$.assigneeName")).isEqualTo("Ravi Sen");
        // Only DEPT_ADMINs covering this department: not Water's, not field officers.
        assertThat(JsonPath.<List<String>>read(body, "$.departmentAdmins")).containsExactly("Sanitation Admin");
    }

    @Test
    void overviewNamesTheCategoryInTheCallersLanguage() throws Exception {
        String id = file();
        String body = mvc.perform(get("/api/v1/grievances/{id}/overview", id).with(as(citizen))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "bn-IN,bn;q=0.9,en;q=0.8"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(body, "$.categoryName")).isEqualTo("আবর্জনা সংগ্রহ হয়নি");
    }

    @Test
    void overviewIsUnassignedBeforeAssignment() throws Exception {
        assertThat(JsonPath.<Object>read(read("overview", file(), citizen), "$.assigneeName")).isNull();
    }

    @Test
    void overviewSaysWhoMayPostWhichKindOfComment() throws Exception {
        String id = file();

        assertThat(JsonPath.<Map<String, Object>>read(read("overview", id, citizen), "$"))
                .containsEntry("canComment", true).containsEntry("canCommentInternal", false);
        assertThat(JsonPath.<Map<String, Object>>read(read("overview", id, officer), "$"))
                .containsEntry("canComment", true).containsEntry("canCommentInternal", true);
        assertThat(JsonPath.<Map<String, Object>>read(read("overview", id, auditor), "$"))
                .containsEntry("canComment", false).containsEntry("canCommentInternal", false);
    }

    // ---------------------------------------------------------------- comments

    @Test
    void citizenCommentIsCreatedAndSeenByEveryone() throws Exception {
        String id = file();

        String created = comment(id, citizen, "{\"body\": \"  Still overflowing today  \"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Map<String, Object>>read(created, "$"))
                .containsEntry("kind", "COMMENT")
                .containsEntry("body", "Still overflowing today")
                .containsEntry("internal", false)
                .containsEntry("actorType", "CITIZEN")
                .containsEntry("actorName", "Asha Das");
        assertThat(JsonPath.<String>read(created, "$.at")).isNotNull();

        for (AppUser viewer : List.of(citizen, officer, admin)) {
            assertThat(ofKind(read("activity", id, viewer), "COMMENT")).singleElement()
                    .satisfies(c -> assertThat(c).containsEntry("body", "Still overflowing today"));
        }
    }

    @Test
    void internalRemarkIsNeverSentToTheCitizen() throws Exception {
        String id = file();
        comment(id, officer, "{\"body\": \"%s\", \"internal\": true}".formatted(INTERNAL_REMARK))
                .andExpect(status().isCreated());

        assertThat(read("activity", id, citizen)).doesNotContain(INTERNAL_REMARK);
        assertThat(ofKind(read("activity", id, citizen), "COMMENT")).isEmpty();
        assertThat(ofKind(read("activity", id, admin), "COMMENT")).singleElement()
                .satisfies(c -> assertThat(c).containsEntry("internal", true).containsEntry("actorName", "Ravi Sen"));
        // Auditors read internal remarks without being able to post.
        assertThat(read("activity", id, auditor)).contains(INTERNAL_REMARK);
    }

    @Test
    void citizenCannotPostAnInternalRemark() throws Exception {
        String id = file();

        comment(id, citizen, "{\"body\": \"Sneaky\", \"internal\": true}").andExpect(status().isForbidden());
        assertThat(ofKind(read("activity", id, admin), "COMMENT")).isEmpty();
    }

    @Test
    void auditorCannotComment() throws Exception {
        comment(file(), auditor, "{\"body\": \"Noted\"}").andExpect(status().isForbidden());
    }

    @Test
    void blankOrOversizedCommentIs400() throws Exception {
        String id = file();

        comment(id, citizen, "{\"body\": \"   \"}").andExpect(status().isBadRequest());
        comment(id, citizen, "{\"body\": \"%s\"}".formatted("x".repeat(2001))).andExpect(status().isBadRequest());
    }

    @Test
    void outsidersGet404LikeAnUnknownGrievance() throws Exception {
        String id = file();

        comment(id, otherCitizen, "{\"body\": \"Me too\"}").andExpect(status().isNotFound());
        comment(id, waterAdmin, "{\"body\": \"Hi\"}").andExpect(status().isNotFound());
        for (String path : List.of("overview", "activity")) {
            mvc.perform(get("/api/v1/grievances/{id}/" + path, id).with(as(otherCitizen)))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/grievances/{id}/" + path, UUID.randomUUID()).with(as(citizen)))
                    .andExpect(status().isNotFound());
        }
    }

    // ---------------------------------------------------------------- events

    @Test
    void assignmentIsVisibleToTheCitizenWithoutTheStaffNote() throws Exception {
        String id = assigned();

        String citizenView = read("activity", id, citizen);
        assertThat(citizenView).doesNotContain(ASSIGN_NOTE);
        assertThat(ofKind(citizenView, "ASSIGNMENT")).singleElement().satisfies(a -> assertThat(a)
                .containsEntry("assigneeName", "Ravi Sen")
                .containsEntry("previousAssigneeName", null)
                .containsEntry("actorName", "Sanitation Admin")
                .containsEntry("reason", null));

        assertThat(ofKind(read("activity", id, officer), "ASSIGNMENT")).singleElement()
                .satisfies(a -> assertThat(a).containsEntry("reason", ASSIGN_NOTE));
    }

    @Test
    void reassignmentAfterReopenNamesThePreviousAssignee() throws Exception {
        UUID id = UUID.fromString(assigned());
        transitions.transition(id, GrievanceStatus.IN_PROGRESS, officer.getId(), null);
        transitions.transition(id, GrievanceStatus.RESOLVED, officer.getId(), "Cleared");
        transitions.transition(id, GrievanceStatus.REOPENED, citizen.getId(), "Overflowing again");
        transitions.assign(id, secondOfficer.getId(), admin.getId(), null);

        List<Map<String, Object>> assignments = ofKind(read("activity", id.toString(), citizen), "ASSIGNMENT");
        assertThat(assignments).hasSize(2);
        assertThat(assignments.get(1))
                .containsEntry("assigneeName", "Mita Paul")
                .containsEntry("previousAssigneeName", "Ravi Sen");
    }

    @Test
    void departmentChangeIsVisibleToTheCitizenWithoutTheReason() throws Exception {
        String id = file();
        // Triage is not built yet: write the audit row it will write.
        em.createNativeQuery("""
                        insert into grievance_reroute (grievance_id, actor_id, old_category_id, new_category_id,
                            old_department_id, new_department_id, old_ward_id, new_ward_id, reason)
                        values (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?7, ?8)""")
                .setParameter(1, UUID.fromString(id))
                .setParameter(2, admin.getId())
                .setParameter(3, garbage.getId())
                .setParameter(4, pipeBurst.getId())
                .setParameter(5, sanitation.getId())
                .setParameter(6, water.getId())
                .setParameter(7, w12.getId())
                .setParameter(8, REROUTE_NOTE)
                .executeUpdate();

        String citizenView = read("activity", id, citizen);
        assertThat(citizenView).doesNotContain(REROUTE_NOTE);
        assertThat(ofKind(citizenView, "REROUTE")).singleElement().satisfies(r -> assertThat(r)
                .containsEntry("fromDepartment", "Sanitation")
                .containsEntry("toDepartment", "Water Supply")
                .containsEntry("fromCategory", "Garbage not collected")
                .containsEntry("toCategory", "Pipe burst")
                .containsEntry("actorName", "Sanitation Admin"));

        assertThat(ofKind(read("activity", id, admin), "REROUTE")).singleElement()
                .satisfies(r -> assertThat(r).containsEntry("reason", REROUTE_NOTE));
    }

    @Test
    void activityIsOldestFirstWithStatusBeforeItsAssignment() throws Exception {
        String id = assigned();
        comment(id, citizen, "{\"body\": \"Thanks\"}").andExpect(status().isCreated());

        List<String> kinds = JsonPath.read(read("activity", id, officer), "$[*].kind");
        assertThat(kinds).containsExactly("STATUS", "STATUS", "STATUS", "ASSIGNMENT", "COMMENT");
    }
}
