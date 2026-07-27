package com.ingesta.messaging;

import com.ingesta.model.Transaction;

public interface TransactionEventPublisher {

    void publish(Transaction transaction);
}