package com.shubha.grievanceportal.identity;

import java.util.UUID;

/** Where an active grant applies. A null axis means unrestricted on that axis. */
public record PermissionScope(UUID departmentId, UUID wardId) {
}
