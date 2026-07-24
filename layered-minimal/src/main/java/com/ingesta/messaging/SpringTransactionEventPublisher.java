package com.ingesta.messaging;

import com.ingesta.model.Transaction;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class SpringTransactionEventPublisher implements TransactionEventPublisher {

    private final ApplicationEventPublisher publisher;

    public SpringTransactionEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(Transaction transaction) {
        publisher.publishEvent(new TransactionIngestedEvent(transaction));
    }
}