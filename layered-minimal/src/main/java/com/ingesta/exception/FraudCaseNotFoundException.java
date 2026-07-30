package com.ingesta.exception;

public class FraudCaseNotFoundException extends RuntimeException {

    public FraudCaseNotFoundException(String transactionId) {
        super("Caso de fraude no encontrado para la transaccion: " + transactionId);
    }
}
