package com.shubha.grievanceportal.shared;

/** A password change was refused: wrong current password, or the new one is the old one. */
public class PasswordRejectedException extends RuntimeException {

    public PasswordRejectedException(String message) {
        super(message);
    }
}
