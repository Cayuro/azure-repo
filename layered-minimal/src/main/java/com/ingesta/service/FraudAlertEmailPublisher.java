package com.ingesta.service;

import com.ingesta.model.Transaction;
import com.ingesta.model.TransactionScore;

public interface FraudAlertEmailPublisher {

    void publicarAlerta(Transaction transaction, TransactionScore score);
}
