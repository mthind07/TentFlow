package com.mthind.tentflow.exception;

//thrown when a reservation status change is not legal
public class InvalidReservationStateException
        extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidReservationStateException(String message) {
        super(message);
    }
}