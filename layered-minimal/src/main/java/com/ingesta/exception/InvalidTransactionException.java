package com.ingesta.exception;

import java.util.List;

public class InvalidTransactionException extends RuntimeException {

    private final List<String> details;

    public InvalidTransactionException(String message, List<String> details) {
        super(message);
        this.details = details;
    }

    public List<String> getDetails() {
        return details;
    }
}
