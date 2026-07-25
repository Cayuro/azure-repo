package com.ingesta.service;

import com.ingesta.model.Transaction;

public interface TransactionEventPublisher {

    void publicarTransaccionIngestada(Transaction transaccion);
}
