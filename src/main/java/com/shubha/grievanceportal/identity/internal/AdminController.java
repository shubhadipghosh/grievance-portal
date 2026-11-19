package com.shubha.grievanceportal.identity.internal;

import com.shubha.grievanceportal.identity.CurrentUser;
import com.shubha.grievanceportal.identity.UserStatus;
import com.shubha.grievanceportal.identity.UserStatusService;
import com.shubha.grievanceportal.identity.UserType;
import com.shubha.grievanceportal.identity.internal.StaffAdminService.CreatedStaff;
import com.shubha.grievanceportal.identity.internal.StaffAdminService.GrantView;
import com.shubha.grievanceportal.identity.internal.StaffAdminService.RoleView;
import com.shubha.grievanceportal.identity.internal.StaffAdminService.UserDetail;
import com.shubha.grievanceportal.identity.internal.StaffAdminService.UserSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Staff accounts, user status and role grants. Every endpoint needs its
 * permission unrestricted (SUPER_ADMIN today); StaffAdminService and
 * UserStatusService check it.
 */
@RestController
@RequestMapping("/api/v1/admin")
class AdminController {

    private final StaffAdminService admin;
    private final UserStatusService userStatus;

    AdminController(StaffAdminService admin, UserStatusService userStatus) {
        this.admin = admin;
        this.userStatus = userStatus;
    }

    // app_user.full_name is VARCHAR(150), email VARCHAR(255).
    record CreateStaffRequest(@NotBlank @Size(max = 150) String fullName,
                              @NotBlank @Email @Size(max = 255) String email) {
    }

    record StatusRequest(@NotNull UserStatus status, @NotBlank @Size(max = 1000) String reason) {
    }

    // Null department or ward: unrestricted on that axis.
    record GrantRequest(@NotBlank String roleCode, UUID departmentId, UUID wardId) {
    }

    record RevokeRequest(@NotBlank @Size(max = 1000) String reason) {
    }

    @GetMapping("/users")
    PagedModel<UserSummary> users(@AuthenticationPrincipal CurrentUser actor,
                                  @RequestParam UserType type,
                                  @RequestParam(required = false) String q,
                                  @PageableDefault(size = 20) Pageable pageable) {
        // By name only: a client-chosen sort could order by any column, password_hash included.
        Pageable byName = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("fullName"));
        return new PagedModel<>(admin.list(actor.id(), type, q, byName));
    }

    @GetMapping("/users/{id}")
    UserDetail user(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID id) {
        return admin.detail(actor.id(), id);
    }

    /** 201 with the generated password: the only time it is ever shown. */
    @PostMapping("/users")
    ResponseEntity<CreatedStaff> createStaff(@AuthenticationPrincipal CurrentUser actor,
                                             @Valid @RequestBody CreateStaffRequest body) {
        CreatedStaff created = admin.createStaff(actor.id(), body.fullName(), body.email());
        return ResponseEntity.created(URI.create("/api/v1/admin/users/" + created.user().id())).body(created);
    }

    /** Lock, disable or reinstate. Ends the user's sessions unless reinstating. */
    @PostMapping("/users/{id}/status")
    ResponseEntity<Void> changeStatus(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID id,
                                      @Valid @RequestBody StatusRequest body) {
        userStatus.changeUserStatus(id, body.status(), actor.id(), body.reason());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{id}/grants")
    ResponseEntity<GrantView> grant(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID id,
                                    @Valid @RequestBody GrantRequest body) {
        GrantView grant = admin.grant(actor.id(), id, body.roleCode(), body.departmentId(), body.wardId());
        return ResponseEntity.status(HttpStatus.CREATED).body(grant);
    }

    @PostMapping("/users/{id}/grants/{grantId}/revoke")
    GrantView revoke(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID id,
                     @PathVariable UUID grantId, @Valid @RequestBody RevokeRequest body) {
        return admin.revoke(actor.id(), id, grantId, body.reason());
    }

    /** The roles a grant may name. */
    @GetMapping("/roles")
    List<RoleView> roles(@AuthenticationPrincipal CurrentUser actor) {
        return admin.grantableRoles(actor.id());
    }
}
