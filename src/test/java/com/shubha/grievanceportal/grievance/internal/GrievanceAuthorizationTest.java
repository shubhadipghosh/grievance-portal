package com.shubha.grievanceportal.grievance.internal;

import com.shubha.grievanceportal.TestcontainersConfiguration;
import com.shubha.grievanceportal.identity.AppUser;
import com.shubha.grievanceportal.identity.AuthorizationQuery;
import com.shubha.grievanceportal.identity.TestUserStatus;
import com.shubha.grievanceportal.identity.UserStatus;
import com.shubha.grievanceportal.organization.Category;
import com.shubha.grievanceportal.organization.Department;
import com.shubha.grievanceportal.organization.Ward;
import com.shubha.grievanceportal.shared.SystemActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.shubha.grievanceportal.grievance.internal.GrievanceTransitions.CITIZEN_OWNER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scoped permission checks: does the actor hold any accepted permission with a
 * grant covering the grievance's department and ward (NULL = unrestricted on
 * that axis), or own the grievance when CITIZEN_OWNER is accepted.
 *
 * <p>Roles, role permissions and the SYSTEM user's grant are seeded by V4/V5.
 * Grants are inserted with SQL so this module's test does not reach into
 * identity's internals.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, GrievanceAuthorization.class})
class GrievanceAuthorizationTest {

    // Registers identity's AuthorizationQuery implementation without naming the
    // package-private class: only the public interface is referenced.
    @TestConfiguration
    @ComponentScan(basePackageClasses = AuthorizationQuery.class, useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                    classes = AuthorizationQuery.class))
    static class AuthorizationQueryConfig {
    }

    @Autowired
    TestEntityManager em;

    @Autowired
    GrievanceRepository grievances;

    @Autowired
    GrievanceAuthorization authorization;

    Department sanitation;
    Department water;
    Ward w12;
    Ward w7;
    AppUser owner;
    Grievance grievance;

    @BeforeEach
    void fixtures() {
        sanitation = em.persist(new Department("SANITATION", "Sanitation"));
        water = em.persist(new Department("WATER", "Water"));
        w12 = em.persist(new Ward("W12", "Ward 12"));
        w7 = em.persist(new Ward("W7", "Ward 7"));
        owner = em.persist(AppUser.citizen("+919800000101", "Asha Das"));
        grievance = grievanceIn(sanitation, w12);
    }

    Grievance grievanceIn(Department dept, Ward ward) {
        Category category = em.persist(new Category("C-" + UUID.randomUUID(), null, dept, 48));
        String ticketNo = "GRV-2026-%06d".formatted(grievances.nextTicketSequence());
        return grievances.save(new Grievance(ticketNo, owner, category, ward, dept,
                "Garbage not collected", "Three days now.", null, null, null,
                OffsetDateTime.now().plusHours(48)));
    }

    AppUser staff(String name) {
        return em.persist(AppUser.staff(name, null, "{bcrypt}x"));
    }

    // Null dept or ward = unrestricted on that axis.
    void grant(AppUser user, String roleCode, Department dept, Ward ward) {
        em.flush();
        em.getEntityManager().createNativeQuery("""
                        insert into user_role_grant (user_id, role_id, department_id, ward_id)
                        select ?1, r.id, ?2, ?3 from role r where r.code = ?4""")
                .setParameter(1, user.getId())
                .setParameter(2, dept == null ? null : dept.getId())
                .setParameter(3, ward == null ? null : ward.getId())
                .setParameter(4, roleCode)
                .executeUpdate();
    }

    void revokeAllGrants(AppUser user) {
        em.getEntityManager()
                .createNativeQuery("update user_role_grant set revoked_at = now() where user_id = ?1")
                .setParameter(1, user.getId())
                .executeUpdate();
    }

    void suspend(AppUser user) {
        TestUserStatus.force(user, UserStatus.LOCKED);
        em.flush();
    }

    boolean authorized(AppUser actor, Grievance g, String... accepted) {
        return authorization.isAuthorized(actor.getId(), g, Set.of(accepted));
    }

    // ---------------------------------------------------------------- staff scope

    @Test
    void superAdminUnscopedGrantAuthorizesAnywhere() {
        AppUser admin = staff("Super Admin");
        grant(admin, "SUPER_ADMIN", null, null);
        Grievance elsewhere = grievanceIn(water, w7);

        assertThat(authorized(admin, grievance, "GRIEVANCE_REJECT")).isTrue();
        assertThat(authorized(admin, elsewhere, "GRIEVANCE_REJECT")).isTrue();
    }

    @Test
    void deptAdminAuthorizedInOwnDepartment() {
        AppUser deptAdmin = staff("Sanitation Admin");
        grant(deptAdmin, "DEPT_ADMIN", sanitation, null);

        assertThat(authorized(deptAdmin, grievance, "GRIEVANCE_REJECT")).isTrue();
    }

    @Test
    void deptAdminRejectedInOtherDepartment() {
        AppUser deptAdmin = staff("Water Admin");
        grant(deptAdmin, "DEPT_ADMIN", water, null);

        assertThat(authorized(deptAdmin, grievance, "GRIEVANCE_REJECT")).isFalse();
    }

    @Test
    void deptAdminWithNullWardAuthorizedInAnyWardOfOwnDepartment() {
        AppUser deptAdmin = staff("Sanitation Admin");
        grant(deptAdmin, "DEPT_ADMIN", sanitation, null);
        Grievance otherWard = grievanceIn(sanitation, w7);

        assertThat(authorized(deptAdmin, grievance, "GRIEVANCE_HOLD")).isTrue();
        assertThat(authorized(deptAdmin, otherWard, "GRIEVANCE_HOLD")).isTrue();
    }

    @Test
    void fieldOfficerAuthorizedInOwnDepartmentAndWard() {
        AppUser officer = staff("Ravi Sen");
        grant(officer, "FIELD_OFFICER", sanitation, w12);

        assertThat(authorized(officer, grievance, "GRIEVANCE_WORK")).isTrue();
    }

    @Test
    void fieldOfficerRejectedInSameDepartmentDifferentWard() {
        AppUser officer = staff("Ravi Sen");
        grant(officer, "FIELD_OFFICER", sanitation, w7);

        assertThat(authorized(officer, grievance, "GRIEVANCE_WORK")).isFalse();
    }

    @Test
    void revokedGrantDoesNotAuthorize() {
        AppUser deptAdmin = staff("Sanitation Admin");
        grant(deptAdmin, "DEPT_ADMIN", sanitation, null);
        revokeAllGrants(deptAdmin);

        assertThat(authorized(deptAdmin, grievance, "GRIEVANCE_REJECT")).isFalse();
    }

    @Test
    void suspendedStaffMemberIsRejectedDespiteActiveGrant() {
        AppUser deptAdmin = staff("Sanitation Admin");
        grant(deptAdmin, "DEPT_ADMIN", sanitation, null);
        assertThat(authorized(deptAdmin, grievance, "GRIEVANCE_REJECT")).isTrue();

        suspend(deptAdmin);

        assertThat(authorized(deptAdmin, grievance, "GRIEVANCE_REJECT")).isFalse();
    }

    // ---------------------------------------------------------------- citizen ownership

    @Test
    void citizenAuthorizedOnOwnGrievanceWhenCitizenOwnerAccepted() {
        assertThat(authorized(owner, grievance, CITIZEN_OWNER)).isTrue();
    }

    @Test
    void citizenRejectedOnAnotherCitizensGrievance() {
        AppUser stranger = em.persist(AppUser.citizen("+919800000102", "Bina Roy"));

        assertThat(authorized(stranger, grievance, CITIZEN_OWNER)).isFalse();
    }

    @Test
    void citizenWithoutGrantRejectedOnStaffTransition() {
        // Owning the grievance confers no staff permission.
        assertThat(authorized(owner, grievance, "GRIEVANCE_REJECT")).isFalse();
    }

    @Test
    void suspendedCitizenRejectedOnOwnGrievance() {
        assertThat(authorized(owner, grievance, CITIZEN_OWNER)).isTrue();

        suspend(owner);

        assertThat(authorized(owner, grievance, CITIZEN_OWNER)).isFalse();
    }

    // ---------------------------------------------------------------- system actor

    @Test
    void systemUserAuthorizedForCloseAuto() {
        AppUser system = em.find(AppUser.class, SystemActor.ID);

        assertThat(authorized(system, grievance, "GRIEVANCE_CLOSE_AUTO")).isTrue();
    }

    @Test
    void systemUserRejectedForEveryOtherPermission() {
        AppUser system = em.find(AppUser.class, SystemActor.ID);
        List<String> others = em.getEntityManager()
                .createNativeQuery("select code from permission where code <> 'GRIEVANCE_CLOSE_AUTO'", String.class)
                .getResultList();
        assertThat(others).contains("GRIEVANCE_CLOSE");

        for (String code : others) {
            assertThat(authorized(system, grievance, code)).as(code).isFalse();
        }
        assertThat(authorized(system, grievance, CITIZEN_OWNER)).as(CITIZEN_OWNER).isFalse();
    }

    // ---------------------------------------------------------------- permission mismatch

    @Test
    void actorHoldingNoneOfTheAcceptedPermissionsRejected() {
        // In scope, but FIELD_OFFICER holds neither permission.
        AppUser officer = staff("Ravi Sen");
        grant(officer, "FIELD_OFFICER", sanitation, w12);

        assertThat(authorized(officer, grievance, "GRIEVANCE_REJECT", "GRIEVANCE_HOLD")).isFalse();
    }
}
