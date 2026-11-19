package com.shubha.grievanceportal.identity.internal;

import com.shubha.grievanceportal.identity.AppUser;
import com.shubha.grievanceportal.identity.AuthorizationQuery;
import com.shubha.grievanceportal.identity.PermissionScope;
import com.shubha.grievanceportal.identity.Role;
import com.shubha.grievanceportal.identity.UserStatus;
import com.shubha.grievanceportal.identity.UserType;
import com.shubha.grievanceportal.organization.Department;
import com.shubha.grievanceportal.organization.Ward;
import com.shubha.grievanceportal.shared.AlreadyExistsException;
import com.shubha.grievanceportal.shared.AlreadyRevokedException;
import com.shubha.grievanceportal.shared.InvalidReferenceException;
import com.shubha.grievanceportal.shared.NotAuthorizedException;
import com.shubha.grievanceportal.shared.NotFoundException;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Staff accounts and role grants. Every operation needs its permission
 * through a grant unrestricted on both axes: today only SUPER_ADMIN holds one.
 * Checked before anything is loaded, so an unauthorised caller learns nothing.
 */
@Service
@Transactional
class StaffAdminService {

    static final String USER_MANAGE = "USER_MANAGE";
    static final String ROLE_GRANT = "ROLE_GRANT";
    static final String CREATED_REASON = "Staff account created";
    static final String SYSTEM_ROLE = "SYSTEM";

    private static final PermissionScope UNSCOPED = new PermissionScope(null, null);
    // No 0/O, 1/l/I: the admin reads it out or writes it down.
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int TEMPORARY_PASSWORD_LENGTH = 16;

    private final AppUserRepository users;
    private final UserRoleGrantRepository grants;
    private final RoleRepository roles;
    private final UserStatusHistoryRepository statusHistory;
    private final AuthorizationQuery authorizationQuery;
    private final PasswordEncoder encoder;
    private final EntityManager entityManager;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    StaffAdminService(AppUserRepository users, UserRoleGrantRepository grants, RoleRepository roles,
                      UserStatusHistoryRepository statusHistory, AuthorizationQuery authorizationQuery,
                      PasswordEncoder encoder, EntityManager entityManager, Clock clock) {
        this.users = users;
        this.grants = grants;
        this.roles = roles;
        this.statusHistory = statusHistory;
        this.authorizationQuery = authorizationQuery;
        this.encoder = encoder;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    record UserSummary(UUID id, UserType type, String fullName, String email, String phone, UserStatus status) {

        static UserSummary of(AppUser u) {
            return new UserSummary(u.getId(), u.getUserType(), u.getFullName(), u.getEmail(), u.getPhone(),
                    u.getStatus());
        }
    }

    /** @param grants every grant ever held, oldest first: revoked ones are the audit trail */
    record UserDetail(UUID id, UserType type, String fullName, String email, String phone, UserStatus status,
                      boolean mustChangePassword, OffsetDateTime createdAt, List<GrantView> grants) {
    }

    /** Names are null where the id is: an unscoped axis, or a grant seeded with no granter. */
    record GrantView(UUID id, String roleCode, UUID departmentId, String departmentName, UUID wardId,
                     String wardName, String grantedByName, OffsetDateTime grantedAt, OffsetDateTime revokedAt,
                     String revokedByName, String revokeReason) {

        static GrantView of(UserRoleGrant g) {
            Department department = g.getDepartment();
            Ward ward = g.getWard();
            return new GrantView(g.getId(), g.getRole().getCode(),
                    department == null ? null : department.getId(),
                    department == null ? null : department.getName(),
                    ward == null ? null : ward.getId(),
                    ward == null ? null : ward.getName(),
                    g.getGrantedBy() == null ? null : g.getGrantedBy().getFullName(),
                    g.getGrantedAt(), g.getRevokedAt(),
                    g.getRevokedBy() == null ? null : g.getRevokedBy().getFullName(),
                    g.getRevokeReason());
        }
    }

    record RoleView(String code, String description) {
    }

    /** The generated password is returned this once and stored only as a hash. */
    record CreatedStaff(UserDetail user, String temporaryPassword) {
    }

    /** Never the SYSTEM user. query matches name, email or phone, ignoring case. */
    @Transactional(readOnly = true)
    Page<UserSummary> list(UUID actorId, UserType type, String query, Pageable pageable) {
        require(actorId, USER_MANAGE);
        if (query == null || query.isBlank()) {
            return users.findByUserType(type, pageable).map(UserSummary::of);
        }
        String pattern = "%" + query.strip().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return users.search(type, pattern, pageable).map(UserSummary::of);
    }

    @Transactional(readOnly = true)
    UserDetail detail(UUID actorId, UUID userId) {
        require(actorId, USER_MANAGE);
        return detail(load(userId));
    }

    @Transactional(readOnly = true)
    List<RoleView> grantableRoles(UUID actorId) {
        require(actorId, ROLE_GRANT);
        return roles.findAll().stream()
                .filter(r -> !r.getCode().equals(SYSTEM_ROLE))
                .sorted(Comparator.comparing(Role::getCode))
                .map(r -> new RoleView(r.getCode(), r.getDescription()))
                .toList();
    }

    /**
     * @throws AlreadyExistsException another account has this email
     */
    CreatedStaff createStaff(UUID actorId, String fullName, String email) {
        require(actorId, USER_MANAGE);
        String normalized = AppUser.normalizeEmail(email);
        if (users.findByEmail(normalized).isPresent()) {
            throw new AlreadyExistsException("An account with this email already exists");
        }
        String password = temporaryPassword();
        String hash = encoder.encode(password);
        AppUser user = AppUser.staff(fullName.strip(), normalized, hash);
        user.setTemporaryPassword(hash);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Lost a race with another create for the same email.
            throw new AlreadyExistsException("An account with this email already exists");
        }
        statusHistory.save(UserStatusHistory.created(user, users.getReferenceById(actorId), CREATED_REASON));
        // Pick up created_at, a DB default.
        entityManager.refresh(user);
        return new CreatedStaff(detail(user), password);
    }

    /**
     * @param departmentId null = every department
     * @param wardId       null = every ward
     * @throws InvalidReferenceException not a staff account; unknown role, department or ward; the SYSTEM role
     * @throws AlreadyExistsException    the user already holds this role in this scope
     */
    GrantView grant(UUID actorId, UUID userId, String roleCode, UUID departmentId, UUID wardId) {
        require(actorId, ROLE_GRANT);
        notSelf(actorId, userId);
        AppUser user = load(userId);
        if (user.getUserType() != UserType.STAFF) {
            throw new InvalidReferenceException("Roles can only be granted to staff accounts");
        }
        Role role = roles.findByCode(roleCode)
                .filter(r -> !r.getCode().equals(SYSTEM_ROLE))
                .orElseThrow(() -> new InvalidReferenceException("No grantable role " + roleCode));
        Department department = departmentId == null ? null : find(Department.class, departmentId, "department");
        Ward ward = wardId == null ? null : find(Ward.class, wardId, "ward");

        UserRoleGrant grant = new UserRoleGrant(user, role, department, ward, users.getReferenceById(actorId));
        try {
            grants.saveAndFlush(grant);
        } catch (DataIntegrityViolationException e) {
            // uq_active_grant
            throw new AlreadyExistsException("The user already holds " + roleCode + " in this scope");
        }
        // Pick up granted_at, a DB default.
        entityManager.refresh(grant);
        return GrantView.of(grant);
    }

    /**
     * @throws NotFoundException       no such grant on this user
     * @throws AlreadyRevokedException already revoked
     */
    GrantView revoke(UUID actorId, UUID userId, UUID grantId, String reason) {
        require(actorId, ROLE_GRANT);
        notSelf(actorId, userId);
        load(userId);
        UserRoleGrant grant = grants.findForUpdate(grantId)
                .filter(g -> g.getUser().getId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Grant", grantId));
        if (!grant.isActive()) {
            throw new AlreadyRevokedException(grantId);
        }
        grant.revoke(OffsetDateTime.now(clock), users.getReferenceById(actorId), reason.strip());
        grants.flush();
        return GrantView.of(grant);
    }

    private UserDetail detail(AppUser u) {
        List<GrantView> history = grants.findWithDetailsByUserId(u.getId()).stream().map(GrantView::of).toList();
        return new UserDetail(u.getId(), u.getUserType(), u.getFullName(), u.getEmail(), u.getPhone(),
                u.getStatus(), u.isMustChangePassword(), u.getCreatedAt(), history);
    }

    private void require(UUID actorId, String permission) {
        Objects.requireNonNull(actorId, "actorId");
        if (!authorizationQuery.scopesWithPermission(actorId, permission).contains(UNSCOPED)) {
            throw new NotAuthorizedException("Actor " + actorId + " lacks unrestricted " + permission);
        }
    }

    // Granting yourself more, or revoking your own admin grant, both need someone else.
    private static void notSelf(UUID actorId, UUID userId) {
        if (actorId.equals(userId)) {
            throw new NotAuthorizedException("Actor " + actorId + " may not change their own grants");
        }
    }

    // The SYSTEM user is not administered: it looks like no user at all.
    private AppUser load(UUID userId) {
        return users.findById(userId)
                .filter(u -> u.getUserType() != UserType.SYSTEM)
                .orElseThrow(() -> new NotFoundException("User", userId));
    }

    private <T> T find(Class<T> type, UUID id, String name) {
        T found = entityManager.find(type, id);
        if (found == null) {
            throw new InvalidReferenceException("No " + name + " " + id);
        }
        return found;
    }

    private String temporaryPassword() {
        StringBuilder password = new StringBuilder(TEMPORARY_PASSWORD_LENGTH);
        for (int i = 0; i < TEMPORARY_PASSWORD_LENGTH; i++) {
            password.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return password.toString();
    }
}
