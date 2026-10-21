package com.shubha.grievanceportal.shared;

/** A request refers to something that does not exist or cannot be used (e.g. an inactive category). */
public class InvalidReferenceException extends RuntimeException {

    public InvalidReferenceException(String what, Object id) {
        super("Unknown or inactive " + what + ": " + id);
    }

    public InvalidReferenceException(String message) {
        super(message);
    }
}
