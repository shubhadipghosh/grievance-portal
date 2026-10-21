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

Holding **any one** of the listed permissions authorises the transition.

| # | From | To | Accepted permissions | Reason | Notes |
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
| 12 | RESOLVED | CLOSED | CITIZEN_OWNER, GRIEVANCE_CLOSE, GRIEVANCE_CLOSE_AUTO | no | Citizen confirms; staff close on their behalf; or SYSTEM auto-closes |
| 13 | RESOLVED | REOPENED | CITIZEN_OWNER | **yes** | Only if reopen_count = 0 |
| 14 | RESOLVED | IN_PROGRESS | GRIEVANCE_REVERT | **yes** | Staff error correction, dept admin+ |
| 15 | REJECTED | CLOSED | CITIZEN_OWNER, GRIEVANCE_CLOSE, GRIEVANCE_CLOSE_AUTO | no | Citizen acknowledges; staff close on their behalf; or SYSTEM auto-closes |
| 16 | REJECTED | REOPENED | CITIZEN_OWNER | **yes** | Dispute. Only if reopen_count = 0 |
| 17 | REOPENED | ASSIGNED | GRIEVANCE_ASSIGN | no | Back into the normal cycle |
| 18 | REOPENED | REJECTED | GRIEVANCE_REJECT | **yes** | |

**CLOSED is terminal. No outbound transitions.**

Anything not in this table is illegal and must be rejected with a clear
error naming both states.

`CITIZEN_OWNER` is a sentinel, not a row in the permission table. It is
satisfied by `grievance.citizen_id = actor.id`, never by a grant lookup.

## Non-transitions

These change the grievance but NOT its status, and write to their own
tables, not `grievance_status_history`:

- **Reassignment** — new row in `grievance_assignment`, updates
  `current_assignee_id`. Status unchanged.
- **Comment** — row in `grievance_comment`.
- **Attachment upload** — row in `attachment`.
- **Nudge sent** — row in `grievance_nudge`, increments `nudge_count`,
  moves `next_nudge_at`.

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
- Each nudge writes a `grievance_nudge` row: proof of contact if the
  citizen later disputes the auto-close.
- No response after the third nudge → auto-transition to CLOSED
  (transitions 12 / 15), actor = the SYSTEM user (`SystemActor.ID`,
  holding GRIEVANCE_CLOSE_AUTO), recorded in history with reason
  "auto-closed: no citizen response".
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

The `hold_fields_consistent` CHECK makes it impossible to be ON_HOLD
without recording `hold_reason_type`, `hold_owner_type` and `hold_since`.

Hold owner rules (`placeOnHold`), checked after the actor is authorised:

| hold_owner_type | hold_owner_id | hold_owner_note |
|---|---|---|
| CITIZEN | the grievance's own citizen (caller passes neither) | absent |
| STAFF | required: an existing, ACTIVE staff user | optional |
| EXTERNAL | absent (an external party is not a user) | required, names the party |

**AWAITING_CITIZEN requires owner type CITIZEN.** The reason type alone
decides whether the clock pauses, so a citizen-wait owned by anyone else
would pause the SLA while the municipality waits on itself.

## Permissions

| Code | Held by |
|---|---|
| GRIEVANCE_TRIAGE | dept admin, super admin |
| GRIEVANCE_ASSIGN | dept admin, super admin |
| GRIEVANCE_WORK | field officer, dept admin, super admin |
| GRIEVANCE_HOLD | dept admin, super admin |
| GRIEVANCE_REJECT | dept admin, super admin |
| GRIEVANCE_REVERT | dept admin, super admin |
| GRIEVANCE_CLOSE | dept admin, super admin |
| GRIEVANCE_CLOSE_AUTO | SYSTEM only |
| GRIEVANCE_VIEW_INTERNAL | all staff, auditor |
| GRIEVANCE_COMMENT | all staff |

Field officers deliberately cannot reject, hold, revert, or close. Those
are decisions with accountability attached and belong to someone
supervising.

`GRIEVANCE_CLOSE` and `GRIEVANCE_CLOSE_AUTO` are separate on purpose. A
human closing a grievance and the scheduler closing it after three
ignored nudges reach the same state by different routes, and the audit
trail must distinguish them. The SYSTEM role holds only
GRIEVANCE_CLOSE_AUTO — it can do exactly one thing and nothing else.

Citizen actions (confirm, acknowledge, reopen) are not permission-based.
They are authorised by ownership: `grievance.citizen_id = actor.id`.

## The SYSTEM actor

Automated transitions go through the same authorization path as everyone
else. There is no bypass.

- One seeded `app_user` row, `user_type = 'SYSTEM'`, fixed id
  `00000000-0000-0000-0000-000000000001`, exposed as `SystemActor.ID`.
- Holds the SYSTEM role, unscoped (department and ward both NULL), whose
  only permission is GRIEVANCE_CLOSE_AUTO.
- `uq_single_system_user` guarantees there is at most one.
- `grievance_status_history.actor_id` is NOT NULL: every status change in
  the system is attributable to a real user row.

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
3. Authorise: actor holds one of the accepted permissions, scoped to the
   grievance's department and ward; or CITIZEN_OWNER is accepted and the
   actor owns the grievance
4. Validate reason present if required
5. Guard reopen_count for transitions 13 and 16
6. Apply side effects (see below)
7. Write grievance_status_history row — never optional
8. Save
9. Publish domain event for notifications (outside the transaction)

### Errors: precise in the service, gated at the API

The service throws a distinct exception per failure (not found, illegal
transition, not authorized, reason required, reopen limit). Callers inside
the system (the scheduler, batch operations, tests) need to tell these
apart, so the service never blurs them.

That precision would leak to outsiders: step 2 runs before step 3, so an
"illegal transition" error reveals a grievance's current status to an actor
with no rights on it, and "not found" vs anything else reveals whether an
id exists. The defence belongs at the API layer, not in the service:

1. **Visibility gate first.** Before calling the service, check `canView`:
   a citizen sees their own grievances; staff see grievances within their
   granted scope. Not visible → **404**, identical to a nonexistent id.
2. **Past the gate**, map precisely. Safe because the actor has already
   shown they can see the grievance.

All mappings live in `shared.ApiExceptionHandler`, as RFC 9457 ProblemDetail:

| Exception | Status |
|---|---|
| `NotFoundException` | 404 |
| `IllegalTransitionException` | 409 |
| `ReopenLimitReachedException` | 409 |
| `OptimisticLockingFailureException` (concurrent change) | 409 |
| `NotAuthorizedException` | 403 |
| `ReasonRequiredException` | 400 |
| Bean Validation, unreadable body, missing/invalid header or param | 400 |
| `IneligibleAssigneeException` | 422 |
| `InvalidHoldDetailsException` | 422 |
| `InvalidReferenceException` (unknown/inactive category or ward) | 422 |
| `InvalidPhoneException` (OTP send) | 400 |
| `RateLimitedException` (OTP send, staff login), with `Retry-After` | 429 |
| `AuthenticationException`: no session, or failed login (fixed detail) | 401 |
| `AccessDeniedException`, including a missing/invalid CSRF token (fixed detail) | 403 |
| anything else | 500, generic detail, logged; no message or stack trace |

`canView` is not transition-specific: every read endpoint uses the same
gate. Lists apply it inside their query rather than after it, so pages and
totals count only what the actor may see; a list never contains a
grievance the actor could not fetch directly.

### Side effects by target state

| Target | Sets |
|---|---|
| RESOLVED | resolved_at, sla_paused_at, nudge_count=0, next_nudge_at=+5d |
| REJECTED | sla_paused_at, nudge_count=0, next_nudge_at=+5d |
| CLOSED | closed_at, next_nudge_at=NULL |
| ON_HOLD | hold_since, hold_reason_type, hold_owner, next_review_at=+7d; sla_paused_at if AWAITING_CITIZEN |
| IN_PROGRESS (from ON_HOLD) | clears hold_*, adds paused duration to total_paused_seconds, extends sla_due_at by the same |
| IN_PROGRESS (from RESOLVED, revert) | resolved_at=NULL, nudge_count=0, next_nudge_at=NULL; credits the time spent RESOLVED (same as leaving ON_HOLD: adds it to total_paused_seconds, extends sla_due_at by it, clears sla_paused_at) |
| REOPENED | reopen_count=1, resolved_at=NULL, clears sla_paused_at (not credited), next_nudge_at=NULL, sla_due_at = now + category.sla_hours × reopen factor (see below); total_paused_seconds unchanged |

Any exit from ON_HOLD (transitions 10 and 11) clears hold_* (the
`hold_fields_consistent` CHECK requires it) and, if the hold paused the
clock, credits the pause first: whole seconds are added to
total_paused_seconds and sla_due_at is extended by the same amount. On
ON_HOLD → REJECTED this happens before REJECTED starts its own clock stop,
so paused time is never lost.

State that has no meaning in the target status is set to NULL rather than
left stale, even where no index or query would currently read it.

### Reopen SLA window

A reopened grievance gets a **fresh, shortened** SLA window:

```
sla_due_at = reopened_at + category.sla_hours × grievance.sla.reopen-factor
```

`grievance.sla.reopen-factor` is a configuration property, default `0.5`,
valid range **0 < factor <= 1.0**. Anything outside it fails startup. A
factor above 1 would give a reopened grievance *more* time than the
original window, inverting the rule's purpose and silently weakening SLA
enforcement.
The window is rounded to whole seconds, like the pause arithmetic.
`total_paused_seconds` is history: it is not carried into the new window,
which is computed fresh.

Why not the alternatives:

- **A full fresh window** would reward premature resolution. A department
  under deadline pressure could resolve early to stop the clock, then get
  a whole new window when the citizen reopens.
- **Keeping the original deadline** would make the reopened grievance
  instantly breached and indistinguishable from one that has been rotting
  for weeks, so nobody could prioritise it.
- **A shortened fresh window** says what happened: the first attempt
  failed, there is less time than originally, and the clock is visibly
  running.

### Resolved late

The queue marks a grievance "Resolved late" when `resolved_at` is after
`sla_due_at`. Both columns describe only the current cycle:

- REOPENED clears `resolved_at` and replaces `sla_due_at` with the reopen
  window, so after a reopen the marker compares the most recent resolution
  against the reopen window, not the original breach.
- A staff revert (RESOLVED → IN_PROGRESS) also clears `resolved_at`.

A breach in an earlier cycle is therefore not visible on the grievance row.
The full history stays in `grievance_status_history`; breach reporting
reads it from there.

### Why three methods, not one

`transition(grievanceId, target, actorId, reason)` handles every target
whose side effects can be derived from the grievance, the clock and the
reason alone. Two targets need input that signature cannot carry:

- **ASSIGNED** (transitions 3, 17) sets `current_assignee_id` and writes a
  `grievance_assignment` row. It needs an assignee.
- **ON_HOLD** (transition 7) sets `hold_reason_type`, `hold_owner_type`,
  `hold_owner_id` / `hold_owner_note`. It needs the hold details.

Each gets its own method that runs the same steps 1–9 with its extra
input. The generic method rejects these two targets with
`IllegalArgumentException` before loading the grievance, so the rejection
depends only on the argument and reveals nothing about the grievance or
the actor's rights.

## Triage (re-routing)

Triage changes a grievance's category and/or department. It is a separate
operation, not part of SUBMITTED → ACKNOWLEDGED: a grievance can turn out
to be misrouted days into a department's queue, and must be fixable after
acknowledgement. It does not change status.

- **Permission in both departments.** The actor needs GRIEVANCE_TRIAGE
  scoped to the **original** department and to the **destination**
  department. Otherwise one department can unilaterally dump work on
  another.
  - Later refinement: an actor without TRIAGE in the destination raises a
    *transfer request* that the destination accepts or declines, instead of
    a direct move.
- **Category change recomputes `sla_due_at` from the original submission
  time**, never from now. Recomputing from now would reset the clock, and
  a breaching grievance would become compliant by reclassification.
- **Ward is immutable.** A citizen reporting a location is reporting a
  fact. A wrong ward is a data error, fixed by an admin correction with its
  own audit, not by triage.
- **Every reroute writes a `grievance_reroute` row** (V6, append-only, with
  a non-blank reason).
- **The reroute row must agree with the grievance. The service guarantees
  this, not the database.** The database cannot check that `old_*` equals
  the grievance's category/department/ward before the change and `new_*`
  equals them after it. A trigger would fire inside the same transaction as
  the update, and the ordering is fragile. Triage tests must therefore read
  both the grievance and its reroute row back after a database round-trip
  and compare category, department and ward.

## Authorization scoping

A grant with NULL on an axis is unrestricted on that axis.

```sql
SELECT EXISTS (
  SELECT 1
  FROM user_role_grant g
         JOIN role_permission rp ON rp.role_id = g.role_id
         JOIN app_user u        ON u.id = g.user_id
  WHERE g.user_id = :actorId
    AND u.status = 'ACTIVE'
    AND g.revoked_at IS NULL
    AND rp.permission_code IN (:codes)
    AND (g.department_id IS NULL OR g.department_id = :departmentId)
    AND (g.ward_id       IS NULL OR g.ward_id       = :wardId)
)
```

| Grant scope | Authorised on |
|---|---|
| (NULL, NULL) | every grievance — super admin, SYSTEM |
| (Sanitation, NULL) | every Sanitation grievance, any ward |
| (Sanitation, Ward 12) | Sanitation grievances in Ward 12 only |

A revoked grant (`revoked_at IS NOT NULL`) never authorises.
A user whose `app_user.status` is not ACTIVE (LOCKED or DISABLED) is
rejected on **both** paths, including citizen ownership. Suspension gates
everything; it is reversible, so grants are left intact rather than revoked.

## What citizens see

`GET /api/v1/grievances/{id}/history` returns the full history only to an
actor holding `GRIEVANCE_VIEW_INTERNAL` with scope covering the grievance
(all staff roles, auditor). Everyone else, which in practice means the
citizen who filed it, gets the redacted view. It fails closed: anyone the
permission check does not clear is treated as a citizen. Both rules live
in `HistoryEntry.forCitizen`, and `GrievanceHistoryApiTest` fails if
either is loosened.

**Rule 1: officers are never named to citizens.** The citizen view
carries the actor's type only (CITIZEN, STAFF, SYSTEM), and the UI shows
"You", "Municipal staff", "System". `actorName` is always null. This is a
safety decision as much as a privacy one: municipal officers have been
harassed over complaint decisions. Reversing it is the stakeholder's
call, not an engineering one.

**Rule 2: staff free text is shown to citizens only where it is written
for them.**

| Entry | Citizen sees the reason? |
|---|---|
| RESOLVED | yes (the resolution note, transition 8) |
| REJECTED | yes |
| REOPENED, CLOSED | only if the citizen wrote it themselves |
| ON_HOLD | never; the hold category (`hold_reason_type`) instead |
| anything else | no |

Hold text often names contractors or records internal frustrations, so
for ON_HOLD the citizen gets the category ("Awaiting contractor") and
never the text. The category is recorded on the history row itself
(V10): `grievance.hold_reason_type` is cleared when the hold ends. Holds
recorded before V10 have no category on their row.

## Implementation note

`Grievance.setStatus()` must NOT be public. The entity exposes only a
package-private `applyTransition(GrievanceStatus, OffsetDateTime)` that
updates status and the matching timestamps together. This makes it
structurally impossible to change status without going through the
service.

The same applies to every field that takes part in authorization or the
state machine. `department` and `ward` are the authorization scope and
`category` drives the SLA, so none of them has a public setter. Department
and category change only through triage (see above). Ward never changes
through routine operations. Of the mutable fields, only `priority` has a
public setter, and a reflection test fails the build if another appears.
