package com.shubha.grievanceportal.grievance.internal;

import com.shubha.grievanceportal.TestcontainersConfiguration;
import com.shubha.grievanceportal.grievance.GrievanceStatus;
import com.shubha.grievanceportal.grievance.Priority;
import com.shubha.grievanceportal.identity.AppUser;
import com.shubha.grievanceportal.organization.Category;
import com.shubha.grievanceportal.organization.Department;
import com.shubha.grievanceportal.organization.Ward;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class GrievanceRepositoriesTest {

    @Autowired
    TestEntityManager em;

    @Autowired
    GrievanceRepository grievances;

    @Autowired
    GrievanceAssignmentRepository assignments;

    @Autowired
    GrievanceStatusHistoryRepository history;

    @Autowired
    GrievanceCommentRepository comments;

    @Autowired
    GrievanceFeedbackRepository feedback;

    AppUser citizen;
    AppUser officer;
    Department dept;
    Ward ward;
    Category category;

    @BeforeEach
    void fixtures() {
        citizen = em.persist(AppUser.citizen("+919800000010", "Asha Das"));
        officer = em.persist(AppUser.staff("Ravi Sen", null, "{bcrypt}x"));
        dept = em.persist(new Department("SANITATION", "Sanitation"));
        ward = em.persist(new Ward("W12", "Ward 12"));
        category = em.persist(new Category("GARBAGE", null, dept, 48));
    }

    Grievance newGrievance(OffsetDateTime slaDueAt) {
        String ticketNo = "GRV-2026-%06d".formatted(grievances.nextTicketSequence());
        return grievances.save(new Grievance(ticketNo, citizen, category, ward, dept,
                "Garbage not collected", "Three days now.", "12 Park St",
                new BigDecimal("22.572600"), new BigDecimal("88.363900"), slaDueAt));
    }

    void setStatus(Grievance g, GrievanceStatus status) {
        em.flush();
        em.getEntityManager()
                // hold_fields_consistent: hold fields are set only while ON_HOLD.
                .createNativeQuery("""
                        update grievance set status = ?1, current_assignee_id = ?2,
                            hold_reason_type = case when ?1 = 'ON_HOLD' then 'OTHER' end,
                            hold_owner_type  = case when ?1 = 'ON_HOLD' then 'STAFF' end,
                            hold_since       = case when ?1 = 'ON_HOLD' then now() end
                        where id = ?3""")
                .setParameter(1, status.name())
                .setParameter(2, officer.getId())
                .setParameter(3, g.getId())
                .executeUpdate();
        em.clear();
    }

    @Test
    void savesWithDatabaseDefaultsAndLazyReferences() {
        Grievance saved = newGrievance(OffsetDateTime.now().plusHours(48));
        assertThat(saved.getId().version()).isEqualTo(7);
        assertThat(saved.getVersion()).isZero();
        em.flush();
        em.clear();

        Grievance loaded = grievances.findByTicketNo(saved.getTicketNo()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(GrievanceStatus.SUBMITTED);
        assertThat(loaded.getPriority()).isEqualTo(Priority.NORMAL);
        assertThat(loaded.getSubmittedAt()).isNotNull();
        assertThat(loaded.getLatitude()).isEqualByComparingTo("22.5726");
        assertThat(Hibernate.isInitialized(loaded.getCitizen())).isFalse();
        assertThat(Hibernate.isInitialized(loaded.getCategory())).isFalse();
    }

    @Test
    void staleUpdateFailsOptimisticLock() {
        Grievance saved = newGrievance(OffsetDateTime.now().plusHours(48));
        em.flush();
        em.clear();

        Grievance stale = grievances.findById(saved.getId()).orElseThrow();
        em.detach(stale);

        Grievance fresh = grievances.findById(saved.getId()).orElseThrow();
        fresh.setPriority(Priority.HIGH);
        grievances.saveAndFlush(fresh);
        assertThat(fresh.getVersion()).isEqualTo(1);

        stale.setPriority(Priority.LOW);
        assertThatThrownBy(() -> grievances.saveAndFlush(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void citizenFinderOrdersNewestFirst() {
        Grievance first = newGrievance(OffsetDateTime.now().plusHours(48));
        em.flush();
        // submitted_at defaults to now(), which is fixed per transaction; force distinct values.
        em.getEntityManager()
                .createNativeQuery("update grievance set submitted_at = now() - interval '1 day' where id = ?1")
                .setParameter(1, first.getId())
                .executeUpdate();
        Grievance second = newGrievance(OffsetDateTime.now().plusHours(48));
        em.flush();
        em.clear();

        assertThat(grievances.findByCitizenIdOrderBySubmittedAtDesc(citizen.getId(), PageRequest.of(0, 10)))
                .containsExactly(second, first);
    }

    /** The queue shows ward and assignee names; loading them lazily would be a query per row. */
    @Test
    void departmentQueueFetchesWardAndAssigneeInItsOwnQuery() {
        Grievance g = newGrievance(OffsetDateTime.now().plusHours(5));
        setStatus(g, GrievanceStatus.IN_PROGRESS);

        assertThat(grievances.findOpenVisibleByDepartmentAndStatus(null, true, Set.of(), dept.getId(),
                GrievanceStatus.IN_PROGRESS, PageRequest.of(0, 10)))
                .singleElement()
                .satisfies(loaded -> {
                    assertThat(Hibernate.isInitialized(loaded.getWard())).isTrue();
                    assertThat(Hibernate.isInitialized(loaded.getCurrentAssignee())).isTrue();
                });
    }

    @Test
    void openQueueFindersExcludeTerminalStatuses() {
        OffsetDateTime now = OffsetDateTime.now();
        Grievance late = newGrievance(now.minusHours(2));
        Grievance onHold = newGrievance(now.minusHours(3));
        Grievance resolved = newGrievance(now.minusHours(4));
        Grievance closed = newGrievance(now.minusHours(5));
        Grievance notDue = newGrievance(now.plusHours(5));
        setStatus(late, GrievanceStatus.IN_PROGRESS);
        setStatus(onHold, GrievanceStatus.ON_HOLD);
        setStatus(resolved, GrievanceStatus.RESOLVED);
        setStatus(closed, GrievanceStatus.CLOSED);
        setStatus(notDue, GrievanceStatus.IN_PROGRESS);

        assertThat(grievances.findSlaDueBefore(now, Limit.of(100)))
                .containsExactly(late);
        assertThat(grievances.findOpenByAssignee(officer.getId(), PageRequest.of(0, 10)))
                .containsExactly(onHold, late, notDue);
        assertThat(grievances.findOpenVisibleByDepartmentAndStatus(null, true, Set.of(), dept.getId(),
                GrievanceStatus.IN_PROGRESS, PageRequest.of(0, 10)))
                .containsExactly(late, notDue);
        assertThat(grievances.findOpenVisibleByDepartmentAndStatus(null, true, Set.of(), dept.getId(),
                GrievanceStatus.CLOSED, PageRequest.of(0, 10)))
                .isEmpty();
    }

    @Test
    void statusHistoryIsInsertOnly() {
        Grievance g = newGrievance(OffsetDateTime.now().plusHours(48));
        GrievanceStatusHistory entry = history.save(
                new GrievanceStatusHistory(g, null, GrievanceStatus.SUBMITTED, citizen, null));
        em.flush();
        em.clear();

        assertThat(history.findByGrievanceIdOrderByOccurredAt(g.getId()))
                .singleElement()
                .satisfies(h -> {
                    assertThat(h.getFromStatus()).isNull();
                    assertThat(h.getToStatus()).isEqualTo(GrievanceStatus.SUBMITTED);
                    assertThat(h.getOccurredAt()).isNotNull();
                });

        assertThat(Arrays.stream(GrievanceStatusHistory.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(Method::getName))
                .noneMatch(name -> name.startsWith("set"));

        // The DB trigger rejects any update that bypasses the entity.
        assertThatThrownBy(() -> em.getEntityManager()
                .createNativeQuery("update grievance_status_history set reason = 'x' where id = ?1")
                .setParameter(1, entry.getId())
                .executeUpdate())
                .hasStackTraceContaining("append-only");
    }

    @Test
    void grievanceExposesNoPublicSetterOnScopeOrStateFields() {
        // Department and ward are the authorization scope, category drives the SLA,
        // and status fields belong to applyTransition. Only priority is free to set.
        assertThat(Arrays.stream(Grievance.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(Method::getName)
                .filter(name -> name.startsWith("set") || name.startsWith("apply")))
                .containsExactly("setPriority");
    }

    @Test
    void assignmentsAndCommentsReferenceGrievance() {
        Grievance g = newGrievance(OffsetDateTime.now().plusHours(48));
        GrievanceAssignment a = assignments.save(new GrievanceAssignment(g, officer, officer, "Initial routing"));
        comments.save(new GrievanceComment(g, citizen, "Any update?", false));
        comments.save(new GrievanceComment(g, officer, "Truck broke down.", true));
        a.end(OffsetDateTime.now());
        em.flush();
        em.clear();

        assertThat(assignments.findByGrievanceIdOrderByAssignedAt(g.getId()))
                .singleElement()
                .satisfies(x -> assertThat(x.isCurrent()).isFalse());
        assertThat(comments.findByGrievanceIdOrderByCreatedAt(g.getId())).hasSize(2);
        assertThat(comments.findByGrievanceIdAndInternalOnlyFalseOrderByCreatedAt(g.getId()))
                .extracting(GrievanceComment::getBody)
                .containsExactly("Any update?");
    }

    @Test
    void feedbackSharesGrievancePrimaryKey() {
        Grievance g = newGrievance(OffsetDateTime.now().plusHours(48));
        feedback.save(new GrievanceFeedback(g, 4, "Fixed quickly"));
        em.flush();
        em.clear();

        GrievanceFeedback loaded = feedback.findById(g.getId()).orElseThrow();
        assertThat(loaded.getGrievanceId()).isEqualTo(g.getId());
        assertThat(loaded.getRating()).isEqualTo((short) 4);
        assertThat(Hibernate.isInitialized(loaded.getGrievance())).isFalse();
        assertThat(feedback.findById(UUID.randomUUID())).isEmpty();
    }
}
