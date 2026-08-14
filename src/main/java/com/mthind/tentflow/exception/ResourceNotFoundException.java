package com.mthind.tentflow.exception;

//thrown when an object cannot be found by its ID
public class ResourceNotFoundException
        extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
