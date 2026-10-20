package com.shubha.grievanceportal.shared;

/** The action requires a non-blank reason and none was given. */
public class ReasonRequiredException extends RuntimeException {

    public ReasonRequiredException(String message) {
        super(message);
    }
}
