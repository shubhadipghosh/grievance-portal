package com.shubha.grievanceportal.shared;

/** Creating this would duplicate something that must be unique. */
public class AlreadyExistsException extends RuntimeException {

    public AlreadyExistsException(String message) {
        super(message);
    }
}
