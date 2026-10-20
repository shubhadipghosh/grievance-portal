package com.shubha.grievanceportal.grievance.internal;

import com.shubha.grievanceportal.TestcontainersConfiguration;
import com.shubha.grievanceportal.grievance.GrievanceStatus;
import com.shubha.grievanceportal.grievance.GrievanceTransitionService;
import com.shubha.grievanceportal.identity.AppUser;
import com.shubha.grievanceportal.identity.AuthorizationQuery;
import com.shubha.grievanceportal.organization.Category;
import com.shubha.grievanceportal.organization.Department;
import com.shubha.grievanceportal.organization.Ward;
import com.shubha.grievanceportal.shared.IllegalTransitionException;
import com.shubha.grievanceportal.shared.NotAuthorizedException;
import com.shubha.grievanceportal.shared.NotFoundException;
import com.shubha.grievanceportal.shared.ReasonRequiredException;
import com.shubha.grievanceportal.shared.ReopenLimitReachedException;
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
import java.util.UUID;

import static com.shubha.grievanceportal.grievance.GrievanceStatus.ACKNOWLEDGED;
import static com.shubha.grievanceportal.grievance.GrievanceStatus.ASSIGNED;
import static com.shubha.grievanceportal.grievance.GrievanceStatus.CLOSED;
import static com.shubha.grievanceportal.grievance.GrievanceStatus.IN_PROGRESS;
import static com.shubha.grievanceportal.grievance.GrievanceStatus.ON_HOLD;
import static com.shubha.grievanceportal.grievance.GrievanceStatus.REJECTED;
import static com.shubha.grievanceportal.grievance.GrievanceStatus.REOPENED;
import static com.shubha.grievanceportal.grievance.GrievanceStatus.RESOLVED;
import static com.shubha.grievanceportal.grievance.GrievanceStatus.SUBMITTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@Import({TestcontainersConfiguration.class, GrievanceTransitionServiceImpl.class, GrievanceAuthorization.class})
class GrievanceTransitionServiceTest {

    // Registers identity's AuthorizationQuery implementation via its public interface.
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
    GrievanceStatusHistoryRepository history;

    @Autowired
    GrievanceTransitionService service;

    Department sanitation;
    Ward w12;
    AppUser citizen;
    AppUser admin;
    AppUser officer;
    Grievance grievance;

    @BeforeEach
    void fixtures() {
        sanitation = em.persist(new Department("SANITATION", "Sanitation"));
        w12 = em.persist(new Ward("W12", "Ward 12"));
        Category category = em.persist(new Category("GARBAGE", null, sanitation, 48));
        citizen = em.persist(AppUser.citizen("+919800000201", "Asha Das"));
        admin = em.persist(AppUser.staff("Sanitation Admin", null, "{bcrypt}x"));
        officer = em.persist(AppUser.staff("Ravi Sen", null, "{bcrypt}x"));
        String ticketNo = "GRV-2026-%06d".formatted(grievances.nextTicketSequence());
        grievance = grievances.save(new Grievance(ticketNo, citizen, category, w12, sanitation,
                "Garbage not collected", "Three days now.", null, null, null,
                OffsetDateTime.now().plusHours(48)));
        grant(admin, "DEPT_ADMIN");
        grant(officer, "FIELD_OFFICER");
    }

    void grant(AppUser user, String roleCode) {
        em.flush();
        em.getEntityManager().createNativeQuery("""
                        insert into user_role_grant (user_id, role_id, department_id, ward_id)
                        select ?1, r.id, ?2, ?3 from role r where r.code = ?4""")
                .setParameter(1, user.getId())
                .setParameter(2, sanitation.getId())
                .setParameter(3, roleCode.equals("FIELD_OFFICER") ? w12.getId() : null)
                .setParameter(4, roleCode)
                .executeUpdate();
    }

    // ASSIGNED is not reachable through the generic method, so tests start from IN_PROGRESS.
    void forceInProgress() {
        em.flush();
        em.getEntityManager()
                .createNativeQuery("update grievance set status = 'IN_PROGRESS', current_assignee_id = ?1 where id = ?2")
                .setParameter(1, officer.getId())
                .setParameter(2, grievance.getId())
                .executeUpdate();
        em.clear();
    }

    void transition(GrievanceStatus target, AppUser actor, String reason) {
        service.transition(grievance.getId(), target, actor.getId(), reason);
        em.flush();
        em.clear();
    }

    Grievance reload() {
        return grievances.findById(grievance.getId()).orElseThrow();
    }

    // ---------------------------------------------------------------- failure modes

    @Test
    void unknownGrievanceIsNotFound() {
        UUID missing = UUID.randomUUID();

        assertThatThrownBy(() -> service.transition(missing, ACKNOWLEDGED, admin.getId(), null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(missing.toString());
    }

    @Test
    void pairNotInTableIsIllegalAndNamesBothStates() {
        assertThatThrownBy(() -> service.transition(grievance.getId(), CLOSED, admin.getId(), null))
                .isInstanceOf(IllegalTransitionException.class)
                .hasMessageContaining("SUBMITTED")
                .hasMessageContaining("CLOSED");
    }

    @Test
    void onHoldAndAssignedAreRejectedBeforeAnyLookup() {
        // Unknown grievance and unknown actor: the rejection depends on the argument
        // alone, so it reveals nothing about any grievance or the actor's rights.
        UUID unknownGrievance = UUID.randomUUID();
        UUID unknownActor = UUID.randomUUID();
        for (GrievanceStatus target : new GrievanceStatus[] {ON_HOLD, ASSIGNED}) {
            assertThatThrownBy(() -> service.transition(unknownGrievance, target, unknownActor, "x"))
                    .as(target.name())
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void nonexistentActorIsNotAuthorizedRatherThanFailingAtFlush() {
        // Guards the getReference in step 7: an id with no app_user row never
        // passes authorization, so it cannot reach the history insert.
        forceInProgress();
        transition(RESOLVED, officer, "bins cleared");

        assertThatThrownBy(() -> service.transition(grievance.getId(), CLOSED, UUID.randomUUID(), null))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void actorWithoutPermissionIsNotAuthorizedAndNothingIsWritten() {
        // Field officers cannot reject.
        assertThatThrownBy(() -> service.transition(grievance.getId(), REJECTED, officer.getId(), "duplicate"))
                .isInstanceOf(NotAuthorizedException.class);

        assertThat(history.findByGrievanceIdOrderByOccurredAt(grievance.getId())).isEmpty();
        assertThat(reload().getStatus()).isEqualTo(SUBMITTED);
    }

    @Test
    void blankReasonOnReasonRequiredTransitionIsRejected() {
        assertThatThrownBy(() -> service.transition(grievance.getId(), REJECTED, admin.getId(), "  "))
                .isInstanceOf(ReasonRequiredException.class);
        assertThatThrownBy(() -> service.transition(grievance.getId(), REJECTED, admin.getId(), null))
                .isInstanceOf(ReasonRequiredException.class);
    }

    @Test
    void secondReopenHitsTheLimit() {
        forceInProgress();
        transition(RESOLVED, officer, "bins cleared");
        transition(REOPENED, citizen, "still there");
        transition(REJECTED, admin, "out of scope");

        assertThatThrownBy(() -> service.transition(grievance.getId(), REOPENED, citizen.getId(), "again"))
                .isInstanceOf(ReopenLimitReachedException.class);
    }

    // ---------------------------------------------------------------- history

    @Test
    void everyTransitionWritesHistoryWithActorFromToAndReason() {
        transition(ACKNOWLEDGED, admin, null);
        transition(REJECTED, admin, "duplicate");

        assertThat(history.findByGrievanceIdOrderByOccurredAt(grievance.getId()))
                .extracting(h -> h.getFromStatus(), h -> h.getToStatus(), h -> h.getActor().getId(), h -> h.getReason())
                .containsExactly(
                        tuple(SUBMITTED, ACKNOWLEDGED, admin.getId(), null),
                        tuple(ACKNOWLEDGED, REJECTED, admin.getId(), "duplicate"));
        assertThat(reload().getStatus()).isEqualTo(REJECTED);
    }

    // ---------------------------------------------------------------- side effects

    @Test
    void resolvedStampsResolutionPausesSlaAndStartsNudgeCycle() {
        forceInProgress();
        transition(RESOLVED, officer, "bins cleared");

        Grievance g = reload();
        assertThat(g.getResolvedAt()).isNotNull();
        assertThat(g.getSlaPausedAt()).isEqualTo(g.getResolvedAt());
        assertThat(g.getNudgeCount()).isZero();
        assertThat(g.getNextNudgeAt()).isEqualTo(g.getResolvedAt().plusDays(5));
    }

    @Test
    void rejectedPausesSlaAndStartsNudgeCycle() {
        transition(REJECTED, admin, "duplicate");

        Grievance g = reload();
        assertThat(g.getResolvedAt()).isNull();
        assertThat(g.getSlaPausedAt()).isNotNull();
        assertThat(g.getNudgeCount()).isZero();
        assertThat(g.getNextNudgeAt()).isEqualTo(g.getSlaPausedAt().plusDays(5));
    }

    @Test
    void systemAutoCloseStampsClosedAndStopsNudges() {
        forceInProgress();
        transition(RESOLVED, officer, "bins cleared");
        AppUser system = em.find(AppUser.class, SystemActor.ID);
        transition(CLOSED, system, null);

        Grievance g = reload();
        assertThat(g.getStatus()).isEqualTo(CLOSED);
        assertThat(g.getClosedAt()).isNotNull();
        assertThat(g.getNextNudgeAt()).isNull();
    }

    @Test
    void reopenedCountsTheReopenAndClearsResolution() {
        forceInProgress();
        transition(RESOLVED, officer, "bins cleared");
        transition(REOPENED, citizen, "still there");

        Grievance g = reload();
        assertThat(g.getReopenCount()).isEqualTo((short) 1);
        assertThat(g.getResolvedAt()).isNull();
        assertThat(g.getSlaPausedAt()).isNull();
        assertThat(g.getNextNudgeAt()).isNull();
    }

    @Test
    void staffRevertUndoesTheResolution() {
        forceInProgress();
        transition(RESOLVED, officer, "bins cleared");
        transition(IN_PROGRESS, admin, "marked resolved by mistake");

        Grievance g = reload();
        assertThat(g.getStatus()).isEqualTo(IN_PROGRESS);
        assertThat(g.getResolvedAt()).isNull();
        assertThat(g.getSlaPausedAt()).isNull();
        assertThat(g.getNudgeCount()).isZero();
        assertThat(g.getNextNudgeAt()).isNull();
    }
}
