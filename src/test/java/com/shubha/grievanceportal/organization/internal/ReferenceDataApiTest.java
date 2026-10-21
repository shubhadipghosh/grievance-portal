package com.shubha.grievanceportal.organization.internal;

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
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Who may list which departments and wards; category names by language. */
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
    Category roads;       // parent; en + bn names
    Category pothole;     // child of roads; en + hi names, no bn
    Category retired;     // inactive
    AppUser officer;      // FIELD_OFFICER, Sanitation, Ward 12
    AppUser deptAdmin;    // DEPT_ADMIN, Sanitation, any ward
    AppUser superAdmin;   // SUPER_ADMIN, unrestricted
    AppUser citizen;
    AppUser ungranted;    // staff with no grant

    @BeforeEach
    void fixtures() {
        sanitation = persist(new Department("TEST_SANITATION", "Sanitation"));
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
        roads = persist(new Category("TEST_ROADS", null, water, 72));
        pothole = persist(new Category("POTHOLE", roads, water, 48));
        retired = persist(new Category("RETIRED", null, water, 48));
        retired.setActive(false);
        em.flush();
        persist(new CategoryTranslation(roads, "en", "Roads", null));
        persist(new CategoryTranslation(roads, "bn", "রাস্তা", null));
        persist(new CategoryTranslation(pothole, "en", "Pothole", null));
        persist(new CategoryTranslation(pothole, "hi", "गड्ढा", null));
        persist(new CategoryTranslation(retired, "en", "Retired", null));
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

    // ---------------------------------------------------------------- categories

    /** GET /categories as the citizen, in listed order. */
    List<Map<String, Object>> categories(String acceptLanguage) throws Exception {
        var principal = new CurrentUser(citizen.getId(), citizen.getUserType());
        var request = get("/api/v1/categories").with(authentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of())));
        if (acceptLanguage != null) {
            request.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
        }
        String body = mvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[*]");
    }

    /** The listed row for the category, or null if it is not listed. */
    static Map<String, Object> row(List<Map<String, Object>> listed, Category category) {
        return listed.stream()
                .filter(r -> category.getId().toString().equals(r.get("id")))
                .findFirst().orElse(null);
    }

    @Test
    void categoryNamesAreInTheRequestedLanguage() throws Exception {
        assertThat(row(categories("bn-IN,bn;q=0.9,en;q=0.8"), roads)).containsEntry("name", "রাস্তা");
    }

    @Test
    void untranslatedCategoryFallsBackToEnglishRatherThanDisappearing() throws Exception {
        assertThat(row(categories("bn"), pothole)).containsEntry("name", "Pothole");
    }

    @Test
    void missingUnknownOrMalformedAcceptLanguageMeansEnglish() throws Exception {
        assertThat(row(categories(null), roads)).containsEntry("name", "Roads");
        assertThat(row(categories("fr-FR"), roads)).containsEntry("name", "Roads");
        assertThat(row(categories("not a ;; header"), roads)).containsEntry("name", "Roads");
    }

    @Test
    void categoriesCarryTheirParent() throws Exception {
        var listed = categories("en");
        assertThat(row(listed, roads)).containsEntry("parentId", null);
        assertThat(row(listed, pothole)).containsEntry("parentId", roads.getId().toString());
    }

    @Test
    void inactiveCategoriesAreNeverListed() throws Exception {
        assertThat(row(categories("en"), retired)).isNull();
    }

    @Test
    void walksTheWholePreferenceListBeforeFallingBackToEnglish() throws Exception {
        // pothole has no Bengali name, but has a Hindi one.
        var listed = categories("bn, hi, en");
        assertThat(row(listed, pothole)).containsEntry("name", "गड्ढा");
        assertThat(row(listed, roads)).containsEntry("name", "রাস্তা");
    }

    @Test
    void languageRefusedWithQZeroIsNotUsed() throws Exception {
        assertThat(row(categories("bn, hi;q=0"), pothole)).containsEntry("name", "Pothole");
    }

    // Only the fixtures: the database's collation, not Java's, decides the
    // order of the seeded names (spaces, case), and that is what is wanted.
    @Test
    void categoriesAreSortedByDisplayedName() throws Exception {
        Set<String> fixtures = ids(roads.getId(), pothole.getId());
        List<String> names = categories("en").stream()
                .filter(r -> fixtures.contains(r.get("id")))
                .map(r -> (String) r.get("name"))
                .toList();
        // roads was created first, so this is not the id order.
        assertThat(names).containsExactly("Pothole", "Roads");
    }

    // ---------------------------------------------------------------- V9 seed

    static final List<String> SEEDED_PARENTS = List.of("SANITATION", "WATER_SUPPLY", "ROADS",
            "STREET_LIGHTING", "DRAINAGE", "PARKS", "PUBLIC_HEALTH", "BUILDING");

    @Test
    void seededTaxonomyHasEightParentsAndTheirLeavesInEnglishAndBengali() {
        Number parents = (Number) em.createNativeQuery("""
                        select count(*) from category where code in (:codes) and parent_id is null""")
                .setParameter("codes", SEEDED_PARENTS)
                .getSingleResult();
        Number leaves = (Number) em.createNativeQuery("""
                        select count(*) from category leaf join category p on p.id = leaf.parent_id
                        where p.code in (:codes)""")
                .setParameter("codes", SEEDED_PARENTS)
                .getSingleResult();
        Number untranslated = (Number) em.createNativeQuery("""
                        select count(*) from category c
                        left join category p on p.id = c.parent_id
                        where (c.code in (:codes) or p.code in (:codes))
                          and (select count(*) from category_translation t
                               where t.category_id = c.id and t.locale in ('en', 'bn')) < 2""")
                .setParameter("codes", SEEDED_PARENTS)
                .getSingleResult();
        assertThat(parents.intValue()).isEqualTo(8);
        assertThat(leaves.intValue()).isBetween(25, 30);
        assertThat(untranslated.intValue()).isZero();
    }

    @Test
    void seededLeafIsListedInBengaliUnderItsParent() throws Exception {
        var listed = categories("bn");
        Map<String, Object> pothole = listed.stream()
                .filter(r -> "রাস্তায় গর্ত".equals(r.get("name"))).findFirst().orElseThrow();
        Map<String, Object> parent = listed.stream()
                .filter(r -> r.get("id").equals(pothole.get("parentId"))).findFirst().orElseThrow();
        assertThat(parent).containsEntry("name", "রাস্তা");
    }
}
