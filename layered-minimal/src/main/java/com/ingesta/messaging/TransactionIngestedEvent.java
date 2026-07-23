package com.ingesta.messaging;

import com.ingesta.model.Transaction;

public record TransactionIngestedEvent(Transaction transaction) {
}