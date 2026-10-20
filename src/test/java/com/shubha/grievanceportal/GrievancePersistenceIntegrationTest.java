package com.shubha.grievanceportal;

import com.shubha.grievanceportal.grievance.internal.Grievance;
import com.shubha.grievanceportal.grievance.GrievanceStatus;
import com.shubha.grievanceportal.grievance.internal.GrievanceRepository;
import com.shubha.grievanceportal.identity.AppUser;
import com.shubha.grievanceportal.identity.UserType;
import com.shubha.grievanceportal.identity.internal.AppUserRepository;
import com.shubha.grievanceportal.organization.Category;
import com.shubha.grievanceportal.organization.Department;
import com.shubha.grievanceportal.organization.Ward;
import com.shubha.grievanceportal.organization.internal.CategoryRepository;
import com.shubha.grievanceportal.organization.internal.DepartmentRepository;
import com.shubha.grievanceportal.organization.internal.WardRepository;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full application context against a real PostgreSQL 18 container. Writes and
 * reads run in separate transactions so the read goes to the database, not the
 * persistence context.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class GrievancePersistenceIntegrationTest {

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    WardRepository wards;

    @Autowired
    DepartmentRepository departments;

    @Autowired
    CategoryRepository categories;

    @Autowired
    AppUserRepository users;

    @Autowired
    GrievanceRepository grievances;

    @Test
    void runsOnPostgres18() {
        Integer versionNum = jdbc.queryForObject("show server_version_num", Integer.class);
        assertThat(versionNum).isBetween(180000, 189999);
    }

    @Test
    void persistedGrievanceReadsBackWithAssociations() {
        // Data is committed and the container is shared with other @SpringBootTest
        // classes, so keep unique columns unique per run.
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String phone = "+9198" + (10_000_000 + Math.abs(suffix.hashCode() % 90_000_000));
        OffsetDateTime slaDueAt = OffsetDateTime.now().plusHours(48);

        UUID grievanceId = tx.execute(status -> {
            Ward ward = wards.save(new Ward("W-" + suffix, "Ward " + suffix));
            Department dept = departments.save(new Department("D-" + suffix, "Sanitation"));
            Category category = categories.save(new Category("C-" + suffix, null, dept, 48));
            AppUser citizen = users.save(AppUser.citizen(phone, "Asha Das"));

            String ticketNo = "GRV-2026-%06d".formatted(grievances.nextTicketSequence());
            return grievances.save(new Grievance(ticketNo, citizen, category, ward, dept,
                    "Garbage not collected", "Bin overflowing for three days.", "12 Park St",
                    new BigDecimal("22.572600"), new BigDecimal("88.363900"), slaDueAt)).getId();
        });

        tx.executeWithoutResult(status -> {
            Grievance g = grievances.findById(grievanceId).orElseThrow();

            // Associations are LAZY: nothing is fetched until touched.
            assertThat(Hibernate.isInitialized(g.getCitizen())).isFalse();
            assertThat(Hibernate.isInitialized(g.getCategory())).isFalse();
            assertThat(Hibernate.isInitialized(g.getWard())).isFalse();
            assertThat(Hibernate.isInitialized(g.getDepartment())).isFalse();

            assertThat(g.getStatus()).isEqualTo(GrievanceStatus.SUBMITTED);
            assertThat(g.getSubmittedAt()).isNotNull();
            assertThat(g.getCurrentAssignee()).isNull();

            assertThat(g.getCitizen().getPhone()).isEqualTo(phone);
            assertThat(g.getCitizen().getUserType()).isEqualTo(UserType.CITIZEN);
            assertThat(g.getWard().getCode()).isEqualTo("W-" + suffix);
            assertThat(g.getDepartment().getCode()).isEqualTo("D-" + suffix);
            assertThat(g.getCategory().getCode()).isEqualTo("C-" + suffix);
            assertThat(g.getCategory().getSlaHours()).isEqualTo(48);
            // Second hop: category -> its default department, same row as the grievance's.
            assertThat(g.getCategory().getDefaultDepartment()).isEqualTo(g.getDepartment());
            assertThat(g.getCategory().getParent()).isNull();

            assertThat(Hibernate.isInitialized(g.getCitizen())).isTrue();
            assertThat(Hibernate.isInitialized(g.getCategory())).isTrue();
            assertThat(Hibernate.isInitialized(g.getWard())).isTrue();
            assertThat(Hibernate.isInitialized(g.getDepartment())).isTrue();
        });
    }
}
