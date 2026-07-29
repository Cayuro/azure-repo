package com.ingesta.dto;

import com.ingesta.model.FraudCase;
import com.ingesta.model.RuleActivation;
import com.ingesta.model.TransactionScore;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record RiesgoResponse(
        String transactionId,
        boolean scored,
        Integer score,
        Integer threshold,
        Instant scoredAt,
        List<RuleActivationResponse> activations,
        FraudCaseResponse fraudCase
) {
    public static RiesgoResponse pending(String transactionId) {
        return new RiesgoResponse(transactionId, false, null, null, null, List.of(), null);
    }

    public static RiesgoResponse of(TransactionScore score, Optional<FraudCase> fraudCase) {
        return new RiesgoResponse(
                score.transactionId(),
                true,
                score.score(),
                score.threshold(),
                score.scoredAt(),
                score.activations().stream().map(RuleActivationResponse::from).toList(),
                fraudCase.map(FraudCaseResponse::from).orElse(null)
        );
    }

    public record RuleActivationResponse(String ruleId, int points, List<String> details) {
        public static RuleActivationResponse from(RuleActivation activation) {
            return new RuleActivationResponse(activation.ruleId(), activation.points(), activation.details());
        }
    }

    public record FraudCaseResponse(
            String caseId,
            String transactionId,
            int score,
            String status,
            Instant openedAt,
            List<RuleActivationResponse> activations
    ) {
        public static FraudCaseResponse from(FraudCase fraudCase) {
            return new FraudCaseResponse(
                    fraudCase.caseId(),
                    fraudCase.transactionId(),
                    fraudCase.score(),
                    fraudCase.status(),
                    fraudCase.openedAt(),
                    fraudCase.activations().stream().map(RuleActivationResponse::from).toList()
            );
        }
    }
}