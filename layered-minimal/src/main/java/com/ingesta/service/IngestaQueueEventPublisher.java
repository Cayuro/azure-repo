package com.ingesta.service;

import com.ingesta.model.Transaction;

public interface IngestaQueueEventPublisher {

    void publicarTransaccionIngestada(Transaction transaccion);
}
