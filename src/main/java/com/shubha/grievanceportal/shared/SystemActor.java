package com.shubha.grievanceportal.shared;

import java.util.UUID;

/**
 * The single SYSTEM user seeded by V5. Automated actions (nudge auto-close,
 * escalation) act as this user so they go through normal authorization and
 * every audit row has a real actor.
 */
public final class SystemActor {

    public static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private SystemActor() {
    }
}
