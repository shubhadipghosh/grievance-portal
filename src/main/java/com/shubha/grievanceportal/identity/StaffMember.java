package com.shubha.grievanceportal.identity;

import java.util.UUID;

/** A staff user as a picker lists them. */
public record StaffMember(UUID id, String fullName) {
}
