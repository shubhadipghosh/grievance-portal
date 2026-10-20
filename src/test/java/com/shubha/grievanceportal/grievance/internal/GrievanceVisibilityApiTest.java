package com.shubha.grievanceportal.grievance.internal;

import com.jayway.jsonpath.JsonPath;
import com.shubha.grievanceportal.TestcontainersConfiguration;
import com.shubha.grievanceportal.identity.AppUser;
import com.shubha.grievanceportal.identity.TestUserStatus;
import com.shubha.grievanceportal.identity.UserStatus;
import com.shubha.grievanceportal.organization.Category;
import com.shubha.grievanceportal.organization.Department;
import com.shubha.grievanceportal.organization.Ward;
import com.shubha.grievanceportal.shared.SystemActor;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.shubha.grievanceportal.grievance.internal.GrievanceApiTest.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The canView gate: who may see which grievance, that every denial is a 404
 * indistinguishable from a missing grievance, and that the queue agrees with
 * direct fetches.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class GrievanceVisibilityApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    EntityManager em;

    Department sanitation;
    Department water;
    Ward w12;
    Ward w7;
    AppUser owner;
    AppUser otherCitizen;
    AppUser officer;      // FIELD_OFFICER, Sanitation, Ward 12
    AppUser deptAdmin;    // DEPT_ADMIN, Sanitation, any ward
    AppUser superAdmin;   // SUPER_ADMIN, unrestricted
    AppUser wardAuditor;  // AUDITOR, any department, Ward 7
    AppUser system;

    String sanitationW12;
    String sanitationW7;
    String waterW12;

    @BeforeEach
    void fixtures() throws Exception {
        sanitation = persist(new Department("SANITATION", "Sanitation"));
        water = persist(new Department("WATER", "Water"));
        w12 = persist(new Ward("W12", "Ward 12"));
        w7 = persist(new Ward("W7", "Ward 7"));
        Category garbage = persist(new Category("GARBAGE", null, sanitation, 48));
        Category leak = persist(new Category("LEAK", null, water, 24));
        owner = persist(AppUser.citizen("+919800000401", "Asha Das"));
        otherCitizen = persist(AppUser.citizen("+919800000402", "Bina Roy"));
        officer = persist(AppUser.staff("Ravi Sen", null, "{bcrypt}x"));
        deptAdmin = persist(AppUser.staff("Sanitation Admin", null, "{bcrypt}x"));
        superAdmin = persist(AppUser.staff("Commissioner", null, "{bcrypt}x"));
        wardAuditor = persist(AppUser.staff("Ward 7 Auditor", null, "{bcrypt}x"));
        em.flush();
        grant(officer, "FIELD_OFFICER", sanitation, w12);
        grant(deptAdmin, "DEPT_ADMIN", sanitation, null);
        grant(superAdmin, "SUPER_ADMIN", null, null);
        grant(wardAuditor, "AUDITOR", null, w7);
        system = em.find(AppUser.class, SystemActor.ID);

        sanitationW12 = file(garbage, w12);
        sanitationW7 = file(garbage, w7);
        waterW12 = file(leak, w12);
    }

    <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    void grant(AppUser user, String roleCode, Department dept, Ward ward) {
        em.createNativeQuery("""
                        insert into user_role_grant (user_id, role_id, department_id, ward_id)
                        select ?1, r.id, ?2, ?3 from role r where r.code = ?4""")
                .setParameter(1, user.getId())
                .setParameter(2, dept == null ? null : dept.getId())
                .setParameter(3, ward == null ? null : ward.getId())
                .setParameter(4, roleCode)
                .executeUpdate();
    }

    String file(Category category, Ward ward) throws Exception {
        String body = mvc.perform(post("/api/v1/grievances").with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": "%s", "wardId": "%s", "title": "Problem",
                                 "description": "Three days now."}""".formatted(category.getId(), ward.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    int fetch(AppUser actor, String grievanceId) throws Exception {
        return mvc.perform(get("/api/v1/grievances/{id}", grievanceId).with(as(actor)))
                .andReturn().getResponse().getStatus();
    }

    void expectVisible(AppUser actor, String... ids) throws Exception {
        Set<String> visible = Set.of(ids);
        for (String id : List.of(sanitationW12, sanitationW7, waterW12)) {
            assertThat(fetch(actor, id)).as(id).isEqualTo(visible.contains(id) ? 200 : 404);
        }
    }

    // ---------------------------------------------------------------- who sees what

    @Test
    void citizenSeesOnlyTheirOwnGrievances() throws Exception {
        expectVisible(owner, sanitationW12, sanitationW7, waterW12);
        expectVisible(otherCitizen);
    }

    @Test
    void lockedCitizenCannotSeeTheirOwnGrievance() throws Exception {
        TestUserStatus.force(owner, UserStatus.LOCKED);
        em.flush();
        expectVisible(owner);
    }

    @Test
    void fieldOfficerSeesOnlyTheirDepartmentAndWard() throws Exception {
        expectVisible(officer, sanitationW12);
    }

    @Test
    void deptAdminSeesEveryWardOfTheirDepartment() throws Exception {
        expectVisible(deptAdmin, sanitationW12, sanitationW7);
    }

    @Test
    void superAdminSeesEverything() throws Exception {
        expectVisible(superAdmin, sanitationW12, sanitationW7, waterW12);
    }

    @Test
    void wardOnlyGrantSeesThatWardInEveryDepartment() throws Exception {
        expectVisible(wardAuditor, sanitationW7);
    }

    @Test
    void systemUserSeesNothing() throws Exception {
        // It acts on grievances (auto-close) but deliberately lacks GRIEVANCE_VIEW.
        expectVisible(system);
    }

    // ---------------------------------------------------------------- denial is 404, never 403

    /** Every gated endpoint, as a request against one grievance. */
    Map<String, MockHttpServletRequestBuilder> everyEndpoint(String id, String ticketNo) {
        return Map.of(
                "get", get("/api/v1/grievances/{id}", id),
                "ticketNo", get("/api/v1/grievances").param("ticketNo", ticketNo),
                "acknowledge", post("/api/v1/grievances/{id}/acknowledge", id),
                "assign", json(post("/api/v1/grievances/{id}/assign", id),
                        "{\"assigneeId\": \"%s\"}".formatted(officer.getId())),
                "hold", json(post("/api/v1/grievances/{id}/hold", id), """
                        {"reasonType": "AWAITING_CONTRACTOR", "ownerType": "EXTERNAL",
                         "holdOwnerNote": "Ganguly Contractors", "reason": "parts"}"""),
                "resolve", json(post("/api/v1/grievances/{id}/resolve", id), "{\"reason\": \"done\"}"),
                "close", post("/api/v1/grievances/{id}/close", id));
    }

    static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    String ticketNo(String id) throws Exception {
        String body = mvc.perform(get("/api/v1/grievances/{id}", id).with(as(owner)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.ticketNo");
    }

    @Test
    void everyDenialIsA404IndistinguishableFromAMissingGrievance() throws Exception {
        String hidden = sanitationW7;
        String hiddenTicket = ticketNo(hidden);
        String missing = UUID.randomUUID().toString();
        String missingTicket = "GRV-1999-000000";

        // Field officer: in the department, wrong ward. Other citizen: not theirs. SYSTEM: no view.
        for (AppUser outsider : List.of(otherCitizen, officer, system)) {
            Map<String, MockHttpServletRequestBuilder> denied = everyEndpoint(hidden, hiddenTicket);
            Map<String, MockHttpServletRequestBuilder> absent = everyEndpoint(missing, missingTicket);
            for (String endpoint : denied.keySet()) {
                MvcResult deniedResult = mvc.perform(denied.get(endpoint).with(as(outsider))).andReturn();
                MvcResult absentResult = mvc.perform(absent.get(endpoint).with(as(outsider))).andReturn();
                String label = endpoint + " as " + outsider.getFullName();

                assertThat(deniedResult.getResponse().getStatus()).as(label).isEqualTo(404);
                // Same body once the id itself is masked: nothing tells "hidden" from "absent".
                assertThat(masked(deniedResult, hidden, hiddenTicket)).as(label)
                        .isEqualTo(masked(absentResult, missing, missingTicket));
            }
        }
    }

    static String masked(MvcResult result, String id, String ticketNo) throws Exception {
        return result.getResponse().getContentAsString().replace(id, "ID").replace(ticketNo, "ID");
    }

    // ---------------------------------------------------------------- the queue agrees with fetch

    Set<String> queue(AppUser actor, Department dept) throws Exception {
        String body = mvc.perform(get("/api/v1/grievances").with(as(actor))
                        .param("departmentId", dept.getId().toString())
                        .param("status", "SUBMITTED")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$.content[*].id");
        return new HashSet<>(ids);
    }

    @Test
    void queueShowsExactlyWhatTheActorCouldFetchDirectly() throws Exception {
        Map<Department, List<String>> byDepartment = Map.of(
                sanitation, List.of(sanitationW12, sanitationW7),
                water, List.of(waterW12));

        for (AppUser actor : List.of(owner, otherCitizen, officer, deptAdmin, superAdmin, wardAuditor, system)) {
            for (Map.Entry<Department, List<String>> dept : byDepartment.entrySet()) {
                Set<String> fetchable = new HashSet<>();
                for (String id : dept.getValue()) {
                    if (fetch(actor, id) == 200) {
                        fetchable.add(id);
                    }
                }
                assertThat(queue(actor, dept.getKey()))
                        .as(actor.getFullName() + " in " + dept.getKey().getCode())
                        .isEqualTo(fetchable);
            }
        }
        // Guards against the comparison passing because everything came back empty.
        assertThat(queue(officer, sanitation)).containsExactly(sanitationW12);
        assertThat(queue(deptAdmin, sanitation)).containsExactlyInAnyOrder(sanitationW12, sanitationW7);
    }

    @Test
    void queueTotalsCountOnlyVisibleGrievances() throws Exception {
        mvc.perform(get("/api/v1/grievances").with(as(officer))
                        .param("departmentId", sanitation.getId().toString())
                        .param("status", "SUBMITTED"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat((Integer) JsonPath.read(
                        result.getResponse().getContentAsString(), "$.page.totalElements")).isEqualTo(1));
    }
}
