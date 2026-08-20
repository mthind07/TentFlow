package com.mthind.tentflow.exception;

//keeps authentication failures generic so account existence is not leaked
public class InvalidCredentialsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException() {
        super("Invalid email or password.");
    }
}