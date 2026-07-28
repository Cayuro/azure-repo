package com.ingesta.dto;

import com.ingesta.model.Transaction;

public class TransaccionIngestadaEvento {

    private final String eventType;
    private final Transaction data;

    public TransaccionIngestadaEvento(Transaction data) {
        this.eventType = "TRANSACCION_INGESTADA";
        this.data = data;
    }

    public String getEventType() {
        return eventType;
    }

    public Transaction getData() {
        return data;
    }
}
