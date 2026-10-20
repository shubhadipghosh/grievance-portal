package com.shubha.grievanceportal.shared;

/** The hold owner details do not fit the hold's owner type or reason type. */
public class InvalidHoldDetailsException extends RuntimeException {

    public InvalidHoldDetailsException(String message) {
        super(message);
    }
}
