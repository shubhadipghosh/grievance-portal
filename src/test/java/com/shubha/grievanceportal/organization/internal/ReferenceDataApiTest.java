package com.shubha.grievanceportal.organization.internal;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Who may list which departments and wards. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class ReferenceDataApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    EntityManager em;

    Department sanitation;
    Department water;
    Department closedDept;
    Ward w12;
    Ward w7;
    Ward closedWard;
    AppUser officer;      // FIELD_OFFICER, Sanitation, Ward 12
    AppUser deptAdmin;    // DEPT_ADMIN, Sanitation, any ward
    AppUser superAdmin;   // SUPER_ADMIN, unrestricted
    AppUser citizen;
    AppUser ungranted;    // staff with no grant

    @BeforeEach
    void fixtures() {
        sanitation = persist(new Department("SANITATION", "Sanitation"));
        water = persist(new Department("WATER", "Water"));
        closedDept = persist(new Department("CLOSED", "Closed department"));
        closedDept.setActive(false);
        w12 = persist(new Ward("W12", "Ward 12"));
        w7 = persist(new Ward("W7", "Ward 7"));
        closedWard = persist(new Ward("W99", "Closed ward"));
        closedWard.setActive(false);
        officer = persist(AppUser.staff("Ravi Sen", null, "{bcrypt}x"));
        deptAdmin = persist(AppUser.staff("Sanitation Admin", null, "{bcrypt}x"));
        superAdmin = persist(AppUser.staff("Commissioner", null, "{bcrypt}x"));
        citizen = persist(AppUser.citizen("+919800000501", "Asha Das"));
        ungranted = persist(AppUser.staff("New Joiner", null, "{bcrypt}x"));
        em.flush();
        grant(officer, "FIELD_OFFICER", sanitation, w12);
        grant(deptAdmin, "DEPT_ADMIN", sanitation, null);
        grant(superAdmin, "SUPER_ADMIN", null, null);
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

    Set<String> list(String path, AppUser user) throws Exception {
        var principal = new CurrentUser(user.getId(), user.getUserType());
        String body = mvc.perform(get(path).with(authentication(
                        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$[*].id");
        return Set.copyOf(ids);
    }

    /** Every active row, read straight from the table rather than through the code under test. */
    @SuppressWarnings("unchecked")
    Set<String> allActive(String table) {
        List<Object> ids = em.createNativeQuery("select id from " + table + " where active").getResultList();
        return ids.stream().map(Object::toString).collect(Collectors.toUnmodifiableSet());
    }

    static Set<String> ids(UUID... ids) {
        return Arrays.stream(ids).map(UUID::toString).collect(Collectors.toUnmodifiableSet());
    }

    // ---------------------------------------------------------------- departments

    @Test
    void fieldOfficerSeesOnlyTheirDepartment() throws Exception {
        assertThat(list("/api/v1/departments", officer)).isEqualTo(ids(sanitation.getId()));
    }

    @Test
    void deptAdminSeesOnlyTheirDepartment() throws Exception {
        assertThat(list("/api/v1/departments", deptAdmin)).isEqualTo(ids(sanitation.getId()));
    }

    @Test
    void superAdminSeesEveryActiveDepartment() throws Exception {
        assertThat(list("/api/v1/departments", superAdmin))
                .isEqualTo(allActive("department"))
                .contains(sanitation.getId().toString(), water.getId().toString());
    }

    @Test
    void citizenSeesEveryActiveDepartment() throws Exception {
        assertThat(list("/api/v1/departments", citizen))
                .isEqualTo(allActive("department"))
                .contains(sanitation.getId().toString(), water.getId().toString());
    }

    @Test
    void inactiveDepartmentsAreNeverListed() throws Exception {
        grant(officer, "FIELD_OFFICER", closedDept, w12);
        assertThat(list("/api/v1/departments", officer)).isEqualTo(ids(sanitation.getId()));
        assertThat(list("/api/v1/departments", superAdmin)).doesNotContain(closedDept.getId().toString());
    }

    @Test
    void staffWithoutGrantsSeeNoDepartments() throws Exception {
        assertThat(list("/api/v1/departments", ungranted)).isEmpty();
    }

    @Test
    void revokedGrantsDoNotCount() throws Exception {
        em.createNativeQuery("update user_role_grant set revoked_at = now() where user_id = ?1")
                .setParameter(1, officer.getId())
                .executeUpdate();
        assertThat(list("/api/v1/departments", officer)).isEmpty();
        assertThat(list("/api/v1/wards", officer)).isEmpty();
    }

    // ---------------------------------------------------------------- wards

    @Test
    void fieldOfficerSeesOnlyTheirWard() throws Exception {
        assertThat(list("/api/v1/wards", officer)).isEqualTo(ids(w12.getId()));
    }

    @Test
    void deptAdminWithoutWardScopeSeesEveryActiveWard() throws Exception {
        assertThat(list("/api/v1/wards", deptAdmin))
                .isEqualTo(allActive("ward"))
                .contains(w12.getId().toString(), w7.getId().toString())
                .doesNotContain(closedWard.getId().toString());
    }

    @Test
    void superAdminSeesEveryActiveWard() throws Exception {
        assertThat(list("/api/v1/wards", superAdmin)).isEqualTo(allActive("ward"));
    }

    @Test
    void citizenSeesEveryActiveWard() throws Exception {
        assertThat(list("/api/v1/wards", citizen))
                .isEqualTo(allActive("ward"))
                .contains(w12.getId().toString(), w7.getId().toString());
    }

    @Test
    void staffWithoutGrantsSeeNoWards() throws Exception {
        assertThat(list("/api/v1/wards", ungranted)).isEmpty();
    }
}
