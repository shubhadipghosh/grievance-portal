package com.shubha.grievanceportal.shared;

/** A grant can be revoked once. */
public class AlreadyRevokedException extends RuntimeException {

    public AlreadyRevokedException(Object grantId) {
        super("Grant already revoked: " + grantId);
    }
}
