package com.shubha.grievanceportal.grievance.internal;

import com.shubha.grievanceportal.TestcontainersConfiguration;
import com.shubha.grievanceportal.grievance.GrievanceStatus;
import com.shubha.grievanceportal.grievance.GrievanceTransitionService;
import com.shubha.grievanceportal.grievance.HoldOwnerType;
import com.shubha.grievanceportal.grievance.HoldReasonType;
import com.shubha.grievanceportal.identity.AppUser;
import com.shubha.grievanceportal.identity.AuthorizationQuery;
import com.shubha.grievanceportal.identity.UserStatus;
import com.shubha.grievanceportal.organization.Category;
import com.shubha.grievanceportal.organization.Department;
import com.shubha.grievanceportal.organization.Ward;
import com.shubha.grievanceportal.shared.IllegalTransitionException;
import com.shubha.grievanceportal.shared.IneligibleAssigneeException;
import com.shubha.grievanceportal.shared.InvalidHoldDetailsException;
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
import static com.shubha.grievanceportal.grievance.HoldOwnerType.CITIZEN;
import static com.shubha.grievanceportal.grievance.HoldOwnerType.EXTERNAL;
import static com.shubha.grievanceportal.grievance.HoldOwnerType.STAFF;
import static com.shubha.grievanceportal.grievance.HoldReasonType.AWAITING_CITIZEN;
import static com.shubha.grievanceportal.grievance.HoldReasonType.AWAITING_CONTRACTOR;
import static com.shubha.grievanceportal.grievance.HoldReasonType.AWAITING_DEPARTMENT;
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
    GrievanceAssignmentRepository assignments;

    @Autowired
    GrievanceTransitionService service;

    Department sanitation;
    Ward w12;
    Ward w7;
    AppUser citizen;
    AppUser admin;
    AppUser officer;
    Grievance grievance;

    @BeforeEach
    void fixtures() {
        sanitation = em.persist(new Department("SANITATION", "Sanitation"));
        w12 = em.persist(new Ward("W12", "Ward 12"));
        w7 = em.persist(new Ward("W7", "Ward 7"));
        Category category = em.persist(new Category("GARBAGE", null, sanitation, 48));
        citizen = em.persist(AppUser.citizen("+919800000201", "Asha Das"));
        admin = em.persist(AppUser.staff("Sanitation Admin", null, "{bcrypt}x"));
        officer = em.persist(AppUser.staff("Ravi Sen", null, "{bcrypt}x"));
        String ticketNo = "GRV-2026-%06d".formatted(grievances.nextTicketSequence());
        grievance = grievances.save(new Grievance(ticketNo, citizen, category, w12, sanitation,
                "Garbage not collected", "Three days now.", null, null, null,
                OffsetDateTime.now().plusHours(48)));
        grant(admin, "DEPT_ADMIN", null);
        grant(officer, "FIELD_OFFICER", w12);
    }

    // Always Sanitation; null ward = every ward.
    void grant(AppUser user, String roleCode, Ward ward) {
        em.flush();
        em.getEntityManager().createNativeQuery("""
                        insert into user_role_grant (user_id, role_id, department_id, ward_id)
                        select ?1, r.id, ?2, ?3 from role r where r.code = ?4""")
                .setParameter(1, user.getId())
                .setParameter(2, sanitation.getId())
                .setParameter(3, ward == null ? null : ward.getId())
                .setParameter(4, roleCode)
                .executeUpdate();
    }

    // ASSIGNED is not reachable through the generic method, so tests start from IN_PROGRESS.
    AppUser staff(String name) {
        return em.persist(AppUser.staff(name, null, "{bcrypt}x"));
    }

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

    void assign(AppUser assignee, AppUser actor) {
        service.assign(grievance.getId(), assignee.getId(), actor.getId(), null);
        em.flush();
        em.clear();
    }

    void hold(HoldReasonType reasonType, HoldOwnerType ownerType, UUID ownerId, String note) {
        service.placeOnHold(grievance.getId(), reasonType, ownerType, ownerId, note, admin.getId(), "waiting");
        em.flush();
        em.clear();
    }

    void assertHoldRejected(HoldReasonType reasonType, HoldOwnerType ownerType, UUID ownerId, String note) {
        assertThatThrownBy(() -> service.placeOnHold(
                grievance.getId(), reasonType, ownerType, ownerId, note, admin.getId(), "waiting"))
                .as("%s / %s / id=%s / note=%s", reasonType, ownerType, ownerId, note)
                .isInstanceOf(InvalidHoldDetailsException.class);
    }

    // Moves the pause start into the past so the paused duration is measurable.
    void backdatePause(int hours) {
        em.getEntityManager()
                .createNativeQuery("update grievance set sla_paused_at = sla_paused_at - make_interval(hours => ?1) where id = ?2")
                .setParameter(1, hours)
                .setParameter(2, grievance.getId())
                .executeUpdate();
        em.clear();
    }

    // Mirrors the hold_fields_consistent CHECK.
    void assertHoldFieldsMatchStatus(Grievance g) {
        boolean onHold = g.getStatus() == ON_HOLD;
        assertThat(g.getHoldReasonType() != null).as("hold_reason_type").isEqualTo(onHold);
        assertThat(g.getHoldOwnerType() != null).as("hold_owner_type").isEqualTo(onHold);
        assertThat(g.getHoldSince() != null).as("hold_since").isEqualTo(onHold);
        assertThat(g.getNextReviewAt() != null).as("next_review_at").isEqualTo(onHold);
        if (!onHold) {
            assertThat(g.getHoldOwner()).isNull();
            assertThat(g.getHoldOwnerNote()).isNull();
        }
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

    // ---------------------------------------------------------------- assign

    @Test
    void assignFromAcknowledgedSetsAssigneeAndWritesBothRows() {
        transition(ACKNOWLEDGED, admin, null);
        assign(officer, admin);

        Grievance g = reload();
        assertThat(g.getStatus()).isEqualTo(ASSIGNED);
        assertThat(g.getCurrentAssignee().getId()).isEqualTo(officer.getId());
        assertThat(assignments.findByGrievanceIdOrderByAssignedAt(grievance.getId()))
                .singleElement()
                .satisfies(a -> {
                    assertThat(a.getAssignee().getId()).isEqualTo(officer.getId());
                    assertThat(a.getAssignedBy().getId()).isEqualTo(admin.getId());
                    assertThat(a.isCurrent()).isTrue();
                });
        assertThat(history.findByGrievanceIdOrderByOccurredAt(grievance.getId()))
                .last()
                .satisfies(h -> {
                    assertThat(h.getFromStatus()).isEqualTo(ACKNOWLEDGED);
                    assertThat(h.getToStatus()).isEqualTo(ASSIGNED);
                    assertThat(h.getActor().getId()).isEqualTo(admin.getId());
                });
    }

    @Test
    void assignFromReopenedEndsThePreviousAssignmentAndAgreesWithGrievance() {
        AppUser second = staff("Mita Paul");
        grant(second, "FIELD_OFFICER", w12);
        transition(ACKNOWLEDGED, admin, null);
        assign(officer, admin);
        forceInProgress();
        transition(RESOLVED, officer, "bins cleared");
        transition(REOPENED, citizen, "still there");

        assign(second, admin);

        Grievance g = reload();
        assertThat(g.getStatus()).isEqualTo(ASSIGNED);
        var rows = assignments.findByGrievanceIdOrderByAssignedAt(grievance.getId());
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).isCurrent()).isFalse();
        assertThat(rows).filteredOn(GrievanceAssignment::isCurrent)
                .singleElement()
                .satisfies(a -> assertThat(a.getAssignee().getId())
                        .isEqualTo(g.getCurrentAssignee().getId())
                        .isEqualTo(second.getId()));
    }

    @Test
    void citizenAndSystemUserAreIneligibleAssignees() {
        transition(ACKNOWLEDGED, admin, null);

        for (UUID notStaff : new UUID[] {citizen.getId(), SystemActor.ID}) {
            assertThatThrownBy(() -> service.assign(grievance.getId(), notStaff, admin.getId(), null))
                    .isInstanceOf(IneligibleAssigneeException.class)
                    .hasMessageContaining("not staff");
        }
        assertThat(reload().getStatus()).isEqualTo(ACKNOWLEDGED);
    }

    @Test
    void suspendedStaffIsAnIneligibleAssignee() {
        transition(ACKNOWLEDGED, admin, null);
        em.find(AppUser.class, officer.getId()).setStatus(UserStatus.LOCKED);
        em.flush();

        assertThatThrownBy(() -> service.assign(grievance.getId(), officer.getId(), admin.getId(), null))
                .isInstanceOf(IneligibleAssigneeException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void staffWithoutWorkPermissionInScopeIsAnIneligibleAssignee() {
        // Same department, other ward.
        AppUser elsewhere = staff("Other Ward Officer");
        grant(elsewhere, "FIELD_OFFICER", w7);
        transition(ACKNOWLEDGED, admin, null);

        assertThatThrownBy(() -> service.assign(grievance.getId(), elsewhere.getId(), admin.getId(), null))
                .isInstanceOf(IneligibleAssigneeException.class)
                .hasMessageContaining("GRIEVANCE_WORK");
        assertThat(assignments.findByGrievanceIdOrderByAssignedAt(grievance.getId())).isEmpty();
    }

    @Test
    void actorWithoutAssignPermissionIsNotAuthorized() {
        // Field officers hold GRIEVANCE_WORK, not GRIEVANCE_ASSIGN.
        transition(ACKNOWLEDGED, admin, null);

        assertThatThrownBy(() -> service.assign(grievance.getId(), officer.getId(), officer.getId(), null))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void assignFromAStatusWithoutAnAssignTransitionIsIllegal() {
        assertThatThrownBy(() -> service.assign(grievance.getId(), officer.getId(), admin.getId(), null))
                .isInstanceOf(IllegalTransitionException.class)
                .hasMessageContaining("SUBMITTED");
    }

    // ---------------------------------------------------------------- placeOnHold

    @Test
    void citizenHoldIsOwnedByTheGrievancesCitizenAndPausesTheClock() {
        forceInProgress();
        hold(AWAITING_CITIZEN, CITIZEN, null, null);

        Grievance g = reload();
        assertThat(g.getStatus()).isEqualTo(ON_HOLD);
        assertHoldFieldsMatchStatus(g);
        assertThat(g.getHoldOwner().getId()).isEqualTo(citizen.getId());
        assertThat(g.getHoldOwnerNote()).isNull();
        assertThat(g.getNextReviewAt()).isEqualTo(g.getHoldSince().plusDays(7));
        assertThat(g.getSlaPausedAt()).isEqualTo(g.getHoldSince());
        assertThat(history.findByGrievanceIdOrderByOccurredAt(grievance.getId()))
                .last()
                .satisfies(h -> {
                    assertThat(h.getFromStatus()).isEqualTo(IN_PROGRESS);
                    assertThat(h.getToStatus()).isEqualTo(ON_HOLD);
                    assertThat(h.getReason()).isEqualTo("waiting");
                });
    }

    @Test
    void staffHoldRecordsTheStaffOwnerAndKeepsTheClockRunning() {
        forceInProgress();
        hold(AWAITING_DEPARTMENT, STAFF, officer.getId(), null);

        Grievance g = reload();
        assertHoldFieldsMatchStatus(g);
        assertThat(g.getHoldOwnerType()).isEqualTo(STAFF);
        assertThat(g.getHoldOwner().getId()).isEqualTo(officer.getId());
        assertThat(g.getSlaPausedAt()).isNull();
    }

    @Test
    void externalHoldRecordsTheNamedPartyAndKeepsTheClockRunning() {
        forceInProgress();
        hold(AWAITING_CONTRACTOR, EXTERNAL, null, "Ganguly Contractors");

        Grievance g = reload();
        assertHoldFieldsMatchStatus(g);
        assertThat(g.getHoldOwner()).isNull();
        assertThat(g.getHoldOwnerNote()).isEqualTo("Ganguly Contractors");
        assertThat(g.getSlaPausedAt()).isNull();
    }

    @Test
    void staffHoldRequiresAnActiveStaffOwner() {
        forceInProgress();
        AppUser locked = staff("Locked Officer");
        locked.setStatus(UserStatus.LOCKED);
        em.flush();

        assertHoldRejected(AWAITING_DEPARTMENT, STAFF, null, null);
        assertHoldRejected(AWAITING_DEPARTMENT, STAFF, UUID.randomUUID(), null);
        assertHoldRejected(AWAITING_DEPARTMENT, STAFF, citizen.getId(), null);
        assertHoldRejected(AWAITING_DEPARTMENT, STAFF, locked.getId(), null);
        assertThat(reload().getStatus()).isEqualTo(IN_PROGRESS);
    }

    @Test
    void externalHoldRequiresANoteAndNoOwnerId() {
        forceInProgress();

        assertHoldRejected(AWAITING_CONTRACTOR, EXTERNAL, null, null);
        assertHoldRejected(AWAITING_CONTRACTOR, EXTERNAL, null, "  ");
        assertHoldRejected(AWAITING_CONTRACTOR, EXTERNAL, officer.getId(), "Ganguly Contractors");
        assertThat(reload().getStatus()).isEqualTo(IN_PROGRESS);
    }

    @Test
    void citizenHoldTakesNeitherOwnerIdNorNote() {
        forceInProgress();

        assertHoldRejected(AWAITING_CITIZEN, CITIZEN, citizen.getId(), null);
        assertHoldRejected(AWAITING_CITIZEN, CITIZEN, null, "the citizen");
        assertThat(reload().getStatus()).isEqualTo(IN_PROGRESS);
    }

    @Test
    void awaitingCitizenCannotBeOwnedBySomeoneElse() {
        // Otherwise the clock pauses while the municipality waits on itself.
        forceInProgress();

        assertHoldRejected(AWAITING_CITIZEN, EXTERNAL, null, "Ganguly Contractors");
        assertHoldRejected(AWAITING_CITIZEN, STAFF, officer.getId(), null);
    }

    @Test
    void holdRequiresAReasonAndHoldPermission() {
        forceInProgress();

        assertThatThrownBy(() -> service.placeOnHold(grievance.getId(), AWAITING_CITIZEN, CITIZEN,
                null, null, admin.getId(), " "))
                .isInstanceOf(ReasonRequiredException.class);
        // Field officers cannot pause the SLA clock.
        assertThatThrownBy(() -> service.placeOnHold(grievance.getId(), AWAITING_CITIZEN, CITIZEN,
                null, null, officer.getId(), "waiting"))
                .isInstanceOf(NotAuthorizedException.class);
    }

    // ---------------------------------------------------------------- release

    @Test
    void releasingACitizenHoldExtendsSlaByExactlyThePausedDuration() {
        forceInProgress();
        OffsetDateTime dueBefore = reload().getSlaDueAt();
        hold(AWAITING_CITIZEN, CITIZEN, null, null);
        backdatePause(2);

        transition(IN_PROGRESS, admin, null);

        Grievance g = reload();
        assertThat(g.getStatus()).isEqualTo(IN_PROGRESS);
        assertHoldFieldsMatchStatus(g);
        assertThat(g.getSlaPausedAt()).isNull();
        assertThat(g.getTotalPausedSeconds()).isBetween(7200L, 7260L);
        assertThat(g.getSlaDueAt()).isEqualTo(dueBefore.plusSeconds(g.getTotalPausedSeconds()));
    }

    @Test
    void releasingAContractorHoldLeavesSlaUntouched() {
        forceInProgress();
        OffsetDateTime dueBefore = reload().getSlaDueAt();
        hold(AWAITING_CONTRACTOR, EXTERNAL, null, "Ganguly Contractors");

        transition(IN_PROGRESS, admin, null);

        Grievance g = reload();
        assertHoldFieldsMatchStatus(g);
        assertThat(g.getTotalPausedSeconds()).isZero();
        assertThat(g.getSlaDueAt()).isEqualTo(dueBefore);
    }

    @Test
    void rejectingFromACitizenHoldStillCreditsThePause() {
        // Transition 11: the pause is credited on any exit from ON_HOLD, before
        // REJECTED starts its own clock stop.
        forceInProgress();
        OffsetDateTime dueBefore = reload().getSlaDueAt();
        hold(AWAITING_CITIZEN, CITIZEN, null, null);
        backdatePause(2);

        transition(REJECTED, admin, "citizen never responded");

        Grievance g = reload();
        assertHoldFieldsMatchStatus(g);
        assertThat(g.getTotalPausedSeconds()).isBetween(7200L, 7260L);
        assertThat(g.getSlaDueAt()).isEqualTo(dueBefore.plusSeconds(g.getTotalPausedSeconds()));
        assertThat(g.getSlaPausedAt()).isNotNull();
    }
}
