package com.shubha.grievanceportal.identity;

import java.io.Serializable;
import java.security.Principal;
import java.util.Objects;
import java.util.UUID;

/**
 * The authenticated principal, as stored in the session: who the user is and
 * nothing more. Permissions are deliberately absent. They are looked up per
 * request through {@link AuthorizationQuery}, so a revoked grant takes effect
 * on the next request rather than at the next login.
 *
 * <p>Serializable because Spring Session JDBC stores the security context with
 * Java serialization. The principal name is the user id, which Spring Session
 * indexes, so a user's sessions can be looked up by id.
 */
public record CurrentUser(UUID id, UserType type) implements Principal, Serializable {

    public CurrentUser {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
    }

    @Override
    public String getName() {
        return id.toString();
    }
}
