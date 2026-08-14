package com.mthind.tentflow.exception;

//thrown when an administrative action would consume inventory that is already unavailable
public class InsufficientAvailabilityException
        extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InsufficientAvailabilityException(String message) {
        super(message);
    }
}
