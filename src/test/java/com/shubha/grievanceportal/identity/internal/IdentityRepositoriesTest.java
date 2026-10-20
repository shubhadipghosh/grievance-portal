package com.shubha.grievanceportal.identity.internal;

import com.shubha.grievanceportal.TestcontainersConfiguration;
import com.shubha.grievanceportal.identity.AppUser;
import com.shubha.grievanceportal.identity.Role;
import com.shubha.grievanceportal.identity.UserStatus;
import com.shubha.grievanceportal.identity.UserType;
import com.shubha.grievanceportal.organization.Department;
import com.shubha.grievanceportal.organization.Ward;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class IdentityRepositoriesTest {

    @Autowired
    TestEntityManager em;

    @Autowired
    AppUserRepository users;

    @Autowired
    RoleRepository roles;

    @Autowired
    PermissionRepository permissions;

    @Autowired
    RolePermissionRepository rolePermissions;

    @Autowired
    UserRoleGrantRepository grants;

    @Autowired
    OtpChallengeRepository otps;

    @Test
    void citizenAndStaffSatisfyCheckConstraints() {
        AppUser citizen = users.saveAndFlush(AppUser.citizen("+919800000001", "Asha Das"));
        AppUser staff = users.saveAndFlush(AppUser.staff("Ravi Sen", "ravi@city.gov.in", "{bcrypt}x"));
        assertThat(citizen.getId().version()).isEqualTo(7);
        em.clear();

        AppUser loaded = users.findByPhone("+919800000001").orElseThrow();
        assertThat(loaded.getUserType()).isEqualTo(UserType.CITIZEN);
        assertThat(loaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(loaded.getPreferredLocale()).isEqualTo("en");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(users.findByEmail("ravi@city.gov.in")).contains(staff);

        Object storedType = em.getEntityManager()
                .createNativeQuery("select user_type from app_user where id = ?1")
                .setParameter(1, staff.getId())
                .getSingleResult();
        assertThat(storedType).isEqualTo("STAFF");
    }

    @Test
    void updateBumpsUpdatedAt() {
        AppUser user = users.saveAndFlush(AppUser.citizen("+919800000002", "Old Name"));
        em.clear();
        AppUser loaded = users.findById(user.getId()).orElseThrow();
        OffsetDateTime before = loaded.getUpdatedAt();

        loaded.setFullName("New Name");
        users.saveAndFlush(loaded);
        em.clear();

        assertThat(users.findById(user.getId()).orElseThrow().getUpdatedAt()).isAfter(before);
    }

    @Test
    void rolePermissionsUseCompositeKey() {
        // DEPT_ADMIN and its grievance permissions are seeded by V4 and V5, CITIZEN_LIMIT_OVERRIDE by V7;
        // USER_MANAGE is not granted to it.
        Role role = roles.findByCode("DEPT_ADMIN").orElseThrow();
        Permission userManage = permissions.findById("USER_MANAGE").orElseThrow();
        rolePermissions.save(new RolePermission(role, userManage));
        em.flush();
        em.clear();

        assertThat(rolePermissions.findByIdRoleId(role.getId()))
                .extracting(rp -> rp.getId().getPermissionCode())
                .containsExactlyInAnyOrder(
                        "GRIEVANCE_TRIAGE", "GRIEVANCE_ASSIGN", "GRIEVANCE_WORK",
                        "GRIEVANCE_HOLD", "GRIEVANCE_REJECT", "GRIEVANCE_REVERT",
                        "GRIEVANCE_VIEW", "GRIEVANCE_VIEW_INTERNAL", "GRIEVANCE_COMMENT", "GRIEVANCE_CLOSE", "REPORT_VIEW",
                        "CITIZEN_LIMIT_OVERRIDE", "USER_MANAGE");
        assertThat(roles.findByCode("DEPT_ADMIN")).contains(role);
    }

    @Test
    void grantsAreScopedAndLazy() {
        AppUser admin = users.save(AppUser.staff("Admin", null, "{bcrypt}x"));
        AppUser officer = users.save(AppUser.staff("Officer", null, "{bcrypt}y"));
        Role superAdmin = roles.findByCode("SUPER_ADMIN").orElseThrow();
        Role fieldOfficer = roles.findByCode("FIELD_OFFICER").orElseThrow();
        Department dept = em.persist(new Department("SANITATION", "Sanitation"));
        Ward ward = em.persist(new Ward("W12", "Ward 12"));

        grants.save(new UserRoleGrant(admin, superAdmin, null, null, null));
        UserRoleGrant scoped = grants.save(new UserRoleGrant(officer, fieldOfficer, dept, ward, admin));
        em.flush();
        em.clear();

        UserRoleGrant adminGrant = grants.findByUserIdAndRevokedAtIsNull(admin.getId()).getFirst();
        assertThat(adminGrant.getDepartment()).isNull();
        assertThat(adminGrant.getWard()).isNull();
        assertThat(adminGrant.getGrantedAt()).isNotNull();

        UserRoleGrant loaded = grants.findById(scoped.getId()).orElseThrow();
        assertThat(Hibernate.isInitialized(loaded.getDepartment())).isFalse();
        assertThat(loaded.getDepartment().getId()).isEqualTo(dept.getId());
        assertThat(loaded.getWard().getId()).isEqualTo(ward.getId());
        assertThat(loaded.getGrantedBy()).isEqualTo(admin);

        loaded.revoke(OffsetDateTime.now());
        em.flush();
        assertThat(grants.findByUserIdAndRevokedAtIsNull(officer.getId())).isEmpty();
    }

    @Test
    void duplicateActiveUnscopedGrantIsRejected() {
        AppUser admin = users.save(AppUser.staff("Admin", null, "{bcrypt}x"));
        Role superAdmin = roles.findByCode("SUPER_ADMIN").orElseThrow();
        grants.saveAndFlush(new UserRoleGrant(admin, superAdmin, null, null, null));

        // uq_active_grant COALESCEs NULL scopes, so this must collide.
        assertThatThrownBy(() -> grants.saveAndFlush(new UserRoleGrant(admin, superAdmin, null, null, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void latestOpenOtpIsFound() {
        String phone = "+919800000003";
        OffsetDateTime expiry = OffsetDateTime.now().plusMinutes(5);
        OtpChallenge first = otps.saveAndFlush(new OtpChallenge(phone, "h1", OtpPurpose.LOGIN, expiry));
        first.consume(OffsetDateTime.now(), BurnReason.SUPERSEDED);
        // uq_live_otp_per_phone allows one live LOGIN challenge. Flush the consume
        // now: Hibernate runs pending inserts before updates, so otherwise the
        // second insert would hit the index while the first is still live.
        otps.flush();
        OtpChallenge second = otps.saveAndFlush(new OtpChallenge(phone, "h2", OtpPurpose.LOGIN, expiry));
        second.recordAttempt();
        otps.saveAndFlush(new OtpChallenge(phone, "h3", OtpPurpose.VERIFY_PHONE, expiry));
        em.clear();

        OtpChallenge open = otps
                .findFirstByPhoneAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(phone, OtpPurpose.LOGIN)
                .orElseThrow();
        assertThat(open).isEqualTo(second);
        assertThat(open.getAttempts()).isEqualTo((short) 1);
    }

    @Test
    void verifySetsConsumedVerifiedAndReasonTogether() {
        OtpChallenge challenge = otps.saveAndFlush(new OtpChallenge("+919800000004", "h1", OtpPurpose.LOGIN,
                OffsetDateTime.now().plusMinutes(5)));
        OffsetDateTime at = OffsetDateTime.now();
        challenge.verify(at);
        otps.flush();
        em.clear();

        OtpChallenge loaded = otps.findById(challenge.getId()).orElseThrow();
        assertThat(loaded.getBurnedReason()).isEqualTo(BurnReason.VERIFIED);
        assertThat(loaded.getConsumedAt()).isNotNull();
        assertThat(loaded.getVerifiedAt()).isEqualTo(loaded.getConsumedAt());
    }

    @Test
    void consumeRejectsVerifiedReason() {
        OtpChallenge challenge = new OtpChallenge("+919800000005", "h1", OtpPurpose.LOGIN,
                OffsetDateTime.now().plusMinutes(5));
        assertThatThrownBy(() -> challenge.consume(OffsetDateTime.now(), BurnReason.VERIFIED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(challenge.getConsumedAt()).isNull();
    }
}
