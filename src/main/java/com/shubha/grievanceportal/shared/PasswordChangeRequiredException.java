package com.shubha.grievanceportal.shared;

/** The user signed in with an admin-generated password and must choose their own first. */
public class PasswordChangeRequiredException extends RuntimeException {

    public PasswordChangeRequiredException() {
        super("Choose a new password to continue.");
    }
}
