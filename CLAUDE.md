# Grievance Portal — Project Brief

Municipal grievance/ticketing portal. Citizens file grievances, municipal
staff resolve them under SLA. Responsive web app first.

## Stack (fixed — do not substitute)

- Java 25 (LTS)
- Spring Boot 4.1.x
- PostgreSQL 18
- Maven
- Angular (frontend, later phase — backend first)

Boot 4 notes that trip up code generated from older patterns:
- Jackson 3 is the default. Imports are `tools.jackson.*`, NOT `com.fasterxml.jackson.*`.
- Starters are granular: use `spring-boot-starter-webmvc`, not `-web`.
- Flyway needs `spring-boot-starter-flyway` plus `flyway-database-postgresql`.
- Hibernate 7 under Jakarta EE 11.

## Architecture: modular monolith

Single deployable. Spring Modulith enforces boundaries — there is a test
that fails if a module reaches into another's internals.

```
com.<org>.grievance
├── shared/          # base types, ProblemDetail handler, common utils
├── identity/        # users, roles, permissions, OTP, login
├── organization/    # departments, wards, categories, routing rules
├── grievance/       # tickets, status machine, comments, assignment
├── attachment/      # uploads, object storage
├── sla/             # SLA calculation, escalation jobs
└── notification/    # SMS, email
```

Each module exposes a public API at its top level (service interface,
records, events). Everything else lives in an `internal` subpackage.
Modules communicate via that API or via Spring Modulith events — never
by reaching into another module's repositories.

**Explicitly rejected:** microservices, Kafka, Elasticsearch, JWT,
Kubernetes. Do not introduce these.

## Conventions

- Records for DTOs. No Lombok.
- Flyway owns the schema. `spring.jpa.hibernate.ddl-auto=validate`.
- `spring.jpa.open-in-view=false`
- `spring.threads.virtual.enabled=true`
- All errors as RFC 9457 `ProblemDetail` from one `@RestControllerAdvice`.
  Never return stack traces or ad hoc error maps.
- All endpoints under `/api/v1`.
- Entities never leave the service layer — map to records at the boundary.
- Integration tests use Testcontainers against real Postgres, not H2.

## Authorization model

NOT four hardcoded roles. Permissions granted through scoped role grants:

```
user_role_grant(user_id, role_id, department_id NULL, ward_id NULL)
```

NULL on an axis means unrestricted there.

| Intended user type | Modeled as |
|---|---|
| Admin | SUPER_ADMIN, no department, no ward |
| Dept admin | DEPT_ADMIN, department set |
| Subordinate | FIELD_OFFICER, department + ward set |
| Citizen | no grant |

Every authorization check is "does this user hold permission P with scope
covering this grievance's department and ward."

## Status state machine

```
SUBMITTED → ACKNOWLEDGED → ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED
                                    ↘ ON_HOLD (pauses SLA clock)
                    ↘ REJECTED
         CLOSED → REOPENED → ASSIGNED
```

Implement as an explicit transition table (from, to, required permission),
not scattered `if (status == ...)` checks.

Every transition writes a row to `grievance_status_history`. That table is
append-only, enforced by a database trigger. Never bypass it.

## Working defaults (revisit with stakeholder)

- Anonymous filing: not supported in v1. Citizen identity = phone number.
- ON_HOLD requires DEPT_ADMIN permission and a mandatory reason. Field
  officers cannot pause the SLA clock.
- RESOLVED → CLOSED: citizen confirms, or auto-closes after 7 days of no
  response. Citizen may reopen within that window.

## Build order

1. ✅ Schema (`V1__init_schema.sql`)
2. Entities, repositories, seed data
3. Status state machine + grievance service
4. Security: OTP login (citizens), password login (staff), scoped permissions
5. REST API: controllers, DTOs, pagination, OpenAPI
6. Attachments → object storage
7. SLA scheduler + escalation
8. Angular frontend

Do not jump ahead. Finish and test each step.

## Non-negotiables

- Audit trail is complete and immutable.
- Attachments go to object storage. Never the database, never local disk.
- Citizens are identified by phone, not email. OTP login, not passwords.
- Category names live in `category_translation`, never hardcoded in Java
  or Angular. The portal will need local-language support.
- Mobile-first. Target device is a low-end Android phone on patchy 4G.
