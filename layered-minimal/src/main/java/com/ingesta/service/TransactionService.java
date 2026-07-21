package com.ingesta.service;

import com.ingesta.dto.TransactionRequest;
import com.ingesta.dto.TransactionResponse;
import com.ingesta.exception.InvalidTransactionException;
import com.ingesta.exception.TransactionNotFoundException;
import com.ingesta.model.Transaction;
import com.ingesta.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository repository;
    private final Clock clock;

    public TransactionService(TransactionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public TransactionResponse ingest(TransactionRequest request) {
        validate(request);

        Instant ingestedAt = Instant.now(clock);
        Transaction transaction = new Transaction(
                request.getTransactionId(),
                request.getAccountId(),
                request.getAmount(),
                request.getCurrency().toUpperCase(),
                request.getOccurredAt(),
                ingestedAt,
                request.getLatitude(),
                request.getLongitude(),
                request.getMerchantId(),
                request.getMerchantCategory()
        );

        TransactionRepository.SaveOutcome outcome = repository.saveIfAbsent(transaction);
        if (outcome == TransactionRepository.SaveOutcome.ALREADY_EXISTS) {
            return new TransactionResponse(transaction.getTransactionId(), "YA_RECIBIDA", transaction.getIngestedAt());
        }
        return new TransactionResponse(transaction.getTransactionId(), "RECIBIDA", transaction.getIngestedAt());
    }

    public Transaction getById(String transactionId) {
        return repository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }

    private void validate(TransactionRequest request) {
        List<String> errors = new ArrayList<>();
        if (request.getOccurredAt() != null && request.getOccurredAt().isAfter(Instant.now(clock))) {
            errors.add("occurredAt no puede ser futura");
        }
        if (!errors.isEmpty()) {
            throw new InvalidTransactionException("La transaccion no cumple el contrato", errors);
        }
    }
}
