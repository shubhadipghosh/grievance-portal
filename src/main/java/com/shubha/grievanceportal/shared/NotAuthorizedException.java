package com.shubha.grievanceportal.shared;

/** The actor lacks the permission or ownership the action requires. */
public class NotAuthorizedException extends RuntimeException {

    public NotAuthorizedException(String message) {
        super(message);
    }
}
