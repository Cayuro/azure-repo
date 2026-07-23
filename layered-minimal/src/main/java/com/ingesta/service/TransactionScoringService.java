package com.ingesta.service;

import com.ingesta.messaging.TransactionIngestedEvent;
import com.ingesta.model.FraudCase;
import com.ingesta.model.Transaction;
import com.ingesta.model.TransactionScore;
import com.ingesta.repository.FraudCaseRepository;
import com.ingesta.repository.TransactionRepository;
import com.ingesta.repository.TransactionScoreRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionScoringService {

    private final TransactionRepository transactionRepository;
    private final TransactionScoreRepository scoreRepository;
    private final FraudCaseRepository fraudCaseRepository;
    private final TransactionScoringEngine scoringEngine;
    private final Clock clock;

    public TransactionScoringService(
            TransactionRepository transactionRepository,
            TransactionScoreRepository scoreRepository,
            FraudCaseRepository fraudCaseRepository,
            TransactionScoringEngine scoringEngine,
            Clock clock) {
        this.transactionRepository = transactionRepository;
        this.scoreRepository = scoreRepository;
        this.fraudCaseRepository = fraudCaseRepository;
        this.scoringEngine = scoringEngine;
        this.clock = clock;
    }

    @EventListener
    public void onTransactionIngested(TransactionIngestedEvent event) {
        Transaction transaction = event.transaction();
        List<Transaction> history = transactionRepository.findByAccountId(transaction.accountId()).stream()
                .filter(item -> !item.transactionId().equals(transaction.transactionId()))
                .toList();

        TransactionScore score = scoringEngine.score(transaction, history);
        scoreRepository.save(score);

        if (score.score() > score.threshold()) {
            FraudCase fraudCase = new FraudCase(
                    UUID.randomUUID().toString(),
                    transaction.transactionId(),
                    score.score(),
                    "ABIERTO",
                    Instant.now(clock),
                    score.activations());
            fraudCaseRepository.save(fraudCase);
        }
    }
}