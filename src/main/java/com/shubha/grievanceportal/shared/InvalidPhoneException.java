package com.shubha.grievanceportal.shared;

/** Not a plausible Indian mobile number in E.164 form. */
public class InvalidPhoneException extends RuntimeException {

    public InvalidPhoneException() {
        super("Enter a mobile number as +91 followed by 10 digits.");
    }
}
