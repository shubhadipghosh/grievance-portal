# Grievance Status State Machine

Authoritative specification. The transition table below is the ONLY place
status rules live. No other code may set `grievance.status`.

## States

| Status | Meaning | SLA clock |
|---|---|---|
| SUBMITTED | Filed by citizen, not yet triaged | running |
| ACKNOWLEDGED | Triaged, department confirmed | running |
| ASSIGNED | Has an owner | running |
| IN_PROGRESS | Work underway | running |
| ON_HOLD | Blocked on a dependency | depends on hold type |
| RESOLVED | Work done, awaiting citizen confirmation | stopped |
| REJECTED | Will not be actioned, awaiting citizen acknowledgement | stopped |
| REOPENED | Citizen disputed resolution or rejection | running |
| CLOSED | Terminal | stopped |

## Transition table

| # | From | To | Permission | Reason | Notes |
|---|---|---|---|---|---|
| 1 | SUBMITTED | ACKNOWLEDGED | GRIEVANCE_TRIAGE | no | May change category/department |
| 2 | SUBMITTED | REJECTED | GRIEVANCE_REJECT | **yes** | Duplicate, out of scope, invalid |
| 3 | ACKNOWLEDGED | ASSIGNED | GRIEVANCE_ASSIGN | no | Sets current_assignee_id |
| 4 | ACKNOWLEDGED | REJECTED | GRIEVANCE_REJECT | **yes** | |
| 5 | ASSIGNED | IN_PROGRESS | GRIEVANCE_WORK | no | Assignee starts work |
| 6 | ASSIGNED | REJECTED | GRIEVANCE_REJECT | **yes** | Notifies all participants |
| 7 | IN_PROGRESS | ON_HOLD | GRIEVANCE_HOLD | **yes** | Requires hold_reason_type |
| 8 | IN_PROGRESS | RESOLVED | GRIEVANCE_WORK | **yes** | Resolution note mandatory |
| 9 | IN_PROGRESS | REJECTED | GRIEVANCE_REJECT | **yes** | Notifies all participants |
| 10 | ON_HOLD | IN_PROGRESS | GRIEVANCE_HOLD | no | Dependency cleared |
| 11 | ON_HOLD | REJECTED | GRIEVANCE_REJECT | **yes** | |
| 12 | RESOLVED | CLOSED | CITIZEN_OWNER, GRIEVANCE_CLOSE_AUTO | no | Citizen confirms, or auto after nudge cycle |
| 13 | RESOLVED | REOPENED | — (citizen, own grievance) | **yes** | Only if reopen_count = 0 |
| 14 | RESOLVED | IN_PROGRESS | GRIEVANCE_REVERT | **yes** | Staff error correction, dept admin+ |
| 15 | REJECTED | CLOSED | CITIZEN_OWNER, GRIEVANCE_CLOSE_AUTO | no | Citizen acknowledges, or auto after nudge cycle |
| 16 | REJECTED | REOPENED | — (citizen, own grievance) | **yes** | Dispute. Only if reopen_count = 0 |
| 17 | REOPENED | ASSIGNED | GRIEVANCE_ASSIGN | no | Back into the normal cycle |
| 18 | REOPENED | REJECTED | GRIEVANCE_REJECT | **yes** | |

**CLOSED is terminal. No outbound transitions.**

Anything not in this table is illegal and must be rejected with a clear
error naming both states.

## Non-transitions

These change the grievance but NOT its status, and write to their own
tables, not `grievance_status_history`:

- **Reassignment** — new row in `grievance_assignment`, updates
  `current_assignee_id`. Status unchanged.
- **Comment** — row in `grievance_comment`.
- **Attachment upload** — row in `attachment`.
- **Nudge sent** — increments `nudge_count`, moves `next_nudge_at`.

## Reopen rule

- A citizen may reopen **exactly once** per grievance.
- Allowed from RESOLVED (transition 13) or REJECTED (transition 16).
- Reason is mandatory.
- Sets `reopen_count = 1`. Enforced by CHECK constraint (0 or 1).
- After the reopened cycle reaches CLOSED, the grievance is terminal.
  Further complaints require a new grievance.

## Citizen nudge cycle

Applies to RESOLVED and REJECTED — any state awaiting citizen response.

- Nudge at day 5, day 10, day 15 after entering the state.
- No response after the third nudge → auto-transition to CLOSED
  (transitions 12 / 15, actor = system, recorded in history with
  reason "auto-closed: no citizen response").
- Citizen responding at any point (confirm, acknowledge, or reopen)
  cancels remaining nudges.

## ON_HOLD dependency tracking

ON_HOLD is not a flat state. It records who is blocking and when to chase.

| hold_reason_type | Owner | SLA clock |
|---|---|---|
| AWAITING_CITIZEN | citizen | **paused** |
| AWAITING_CONTRACTOR | external | running |
| AWAITING_DEPARTMENT | other dept | running |
| AWAITING_BUDGET | internal | running |
| OTHER | — | running |

Rationale: the municipality cannot be penalised for a citizen not
responding. A contractor or internal approval is the municipality's own
problem and the clock keeps running.

Every 7 days while ON_HOLD, a status update goes to the citizen, the
assignee, and the department admin, and `next_review_at` moves forward.

## Permissions

| Code | Held by |
|---|---|
| GRIEVANCE_TRIAGE | dept admin, super admin |
| GRIEVANCE_ASSIGN | dept admin, super admin |
| GRIEVANCE_WORK | field officer, dept admin, super admin |
| GRIEVANCE_HOLD | dept admin, super admin |
| GRIEVANCE_REJECT | dept admin, super admin |
| GRIEVANCE_REVERT | dept admin, super admin |
| GRIEVANCE_VIEW_INTERNAL | all staff |
| GRIEVANCE_COMMENT | all staff |
| GRIEVANCE_CLOSE | dept admin, super admin |
| GRIEVANCE_CLOSE_AUTO | SYSTEM user only |

Field officers deliberately cannot reject, hold, or revert. Those are
decisions with accountability attached and belong to someone supervising.

Citizen actions (confirm, acknowledge, reopen) are not permission-based.
They are authorised by ownership: `grievance.citizen_id = actor.id`.

## Service contract

One method. Every status change goes through it.

```java
void transition(UUID grievanceId,
                GrievanceStatus target,
                UUID actorId,
                String reason);
```

Order of operations, all inside one transaction:

1. Load grievance (optimistic lock via @Version)
2. Look up (current, target) in the transition table — unknown pair rejects
3. Authorise: permission scoped to grievance's department + ward,
   or citizen ownership for citizen transitions
4. Validate reason present if required
5. Guard reopen_count for transitions 13 and 16
6. Apply side effects (see below)
7. Write grievance_status_history row — never optional
8. Save
9. Publish domain event for notifications (outside the transaction)

### Side effects by target state

| Target | Sets |
|---|---|
| RESOLVED | resolved_at, sla_paused_at, nudge_count=0, next_nudge_at=+5d |
| REJECTED | sla_paused_at, nudge_count=0, next_nudge_at=+5d |
| CLOSED | closed_at, next_nudge_at=NULL |
| ON_HOLD | hold_since, hold_reason_type, hold_owner, next_review_at=+7d; sla_paused_at if AWAITING_CITIZEN |
| IN_PROGRESS (from ON_HOLD) | clears hold_*, resumes SLA by extending sla_due_at by paused duration |
| REOPENED | reopen_count=1, resolved_at=NULL, clears sla_paused_at, recomputes sla_due_at |

## Implementation note

`Grievance.setStatus()` must NOT be public. The entity exposes only a
package-private `applyTransition(GrievanceStatus, OffsetDateTime)` that
updates status and the matching timestamps together. This makes it
structurally impossible to change status without going through the
service.
