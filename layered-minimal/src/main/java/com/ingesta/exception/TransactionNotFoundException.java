package com.ingesta.exception;

public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(String transactionId) {
        super("Transaccion no encontrada: " + transactionId);
    }
}
