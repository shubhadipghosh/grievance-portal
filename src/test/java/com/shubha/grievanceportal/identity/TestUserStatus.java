package com.shubha.grievanceportal.identity;

/**
 * Test fixtures only. Sets a user's status directly, with no permission check
 * and no user_status_history row, for tests about what a non-ACTIVE user can
 * do rather than about how they got that way. Production code goes through
 * {@link UserStatusService}.
 */
public final class TestUserStatus {

    private TestUserStatus() {
    }

    public static void force(AppUser user, UserStatus status) {
        user.setStatus(status);
    }
}
