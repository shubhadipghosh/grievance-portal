package com.shubha.grievanceportal.shared;

/** The (from, to) status pair is not in the transition table. */
public class IllegalTransitionException extends RuntimeException {

    public IllegalTransitionException(Enum<?> from, Enum<?> to) {
        super("Illegal transition: " + from + " -> " + to);
    }
}
