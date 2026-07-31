package com.ingesta.service;

import com.ingesta.config.ScoringProperties;
import com.ingesta.dto.TransactionRequest;
import com.ingesta.model.RuleActivation;
import com.ingesta.model.Transaction;
import com.ingesta.model.TransactionScore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class TransactionScoringEngine {

    private final ScoringProperties properties;
    private final Clock clock;

    public TransactionScoringEngine(ScoringProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public TransactionScore score(Transaction transaction, List<Transaction> history) {
        Instant scoredAt = Instant.now(clock);
        List<RuleActivation> activations = new ArrayList<>();

        activateVelocityRule(transaction, history, activations);
        activateAmountRule(transaction, history, activations);
        activateGeoImpossibleRule(transaction, history, activations);
        activateMerchantRiskRule(transaction, activations);

        int score = activations.stream().mapToInt(RuleActivation::points).sum();
        return new TransactionScore(transaction.transactionId(), score, properties.getThreshold(), scoredAt, activations);
    }

    private void activateVelocityRule(Transaction transaction, List<Transaction> history, List<RuleActivation> activations) {
        Instant windowStart = transaction.ingestedAt().minus(Duration.ofMinutes(properties.getVelocityWindowMinutes()));
        Instant windowEnd = transaction.ingestedAt();
        long recentTransactions = history.stream()
                .filter(item -> !item.ingestedAt().isBefore(windowStart) && !item.ingestedAt().isAfter(windowEnd))
                .count() + 1;
        if (recentTransactions >= properties.getVelocityMinimumTransactions()) {
            activations.add(new RuleActivation(
                    "VELOCIDAD",
                    properties.getVelocityPoints(),
                    List.of(
                            "transactionsInWindow=" + recentTransactions,
                            "windowMinutes=" + properties.getVelocityWindowMinutes(),
                            "accountId=" + transaction.accountId()
                    )));
        }
    }

    private void activateAmountRule(Transaction transaction, List<Transaction> history, List<RuleActivation> activations) {
        if (history.isEmpty()) {
            return;
        }

        // Precision completa (MathContext.DECIMAL64) para el calculo: redondear a 2
        // decimales ANTES de multiplicar por el multiplicador amplifica el error de
        // redondeo x{multiplicador} y desplaza el umbral real, dando falsos
        // negativos y falsos positivos cerca del limite. El redondeo a 2 decimales
        // se reserva solo para el texto del detalle.
        BigDecimal average = history.stream()
                .map(Transaction::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(history.size()), MathContext.DECIMAL64);
        BigDecimal thresholdAmount = average.multiply(BigDecimal.valueOf(properties.getAmountMultiplier()));

        if (transaction.amount().compareTo(thresholdAmount) > 0) {
            activations.add(new RuleActivation(
                    "MONTO_ATIPICO",
                    properties.getAmountPoints(),
                    List.of(
                            "amount=" + transaction.amount(),
                            "historicalAverage=" + average.setScale(2, RoundingMode.HALF_UP),
                            "multiplier=" + properties.getAmountMultiplier()
                    )));
        }
    }

    private void activateGeoImpossibleRule(Transaction transaction, List<Transaction> history, List<RuleActivation> activations) {
        // Desempate estable por transactionId: sin el thenComparing, en caso de
        // empate de instante Stream.max devuelve "el primero encontrado", y ese
        // orden depende de como el repositorio entregue el historial (hash), asi
        // que el resultado era intermitente entre ejecuciones equivalentes.
        Transaction lastTransaction = history.stream()
                .max(Comparator.comparing(Transaction::ingestedAt)
                        .thenComparing(Transaction::transactionId))
                .orElse(null);
        if (lastTransaction == null) {
            return;
        }

        double distanceKm = haversine(
                lastTransaction.latitude(),
                lastTransaction.longitude(),
                transaction.latitude(),
                transaction.longitude());

        long elapsedSeconds = Duration.between(lastTransaction.ingestedAt(), transaction.ingestedAt()).toSeconds();
        if (elapsedSeconds < 0) {
            // No se puede razonar sobre el orden temporal: el historial trae una
            // transaccion "mas reciente" que en realidad ocurre despues de la
            // actual (reproceso asincrono). Tratarlo como +1s inflaba la velocidad
            // al maximo y generaba un falso positivo; en su lugar no evaluamos la
            // regla para este par.
            return;
        }
        if (elapsedSeconds == 0) {
            elapsedSeconds = 1;
        }

        double speedKmH = distanceKm / (elapsedSeconds / 3600.0);
        if (speedKmH > properties.getGeoMaxSpeedKmH()) {
            activations.add(new RuleActivation(
                    "GEO_IMPOSIBLE",
                    properties.getGeoPoints(),
                    List.of(
                            "distanceKm=" + round(distanceKm),
                            "elapsedSeconds=" + elapsedSeconds,
                            "speedKmH=" + round(speedKmH)
                    )));
        }
    }

    private void activateMerchantRiskRule(Transaction transaction, List<RuleActivation> activations) {
        boolean riskyCategory = properties.getRiskMerchantCategories().stream()
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.equals(transaction.merchantCategory().trim().toLowerCase(Locale.ROOT)));
        if (riskyCategory) {
            activations.add(new RuleActivation(
                    "COMERCIO_RIESGO",
                    properties.getMerchantPoints(),
                    List.of(
                            "merchantCategory=" + transaction.merchantCategory(),
                            "merchantId=" + transaction.merchantId()
                    )));
        }
    }

    private double haversine(Double latitude1, Double longitude1, Double latitude2, Double longitude2) {
        double earthRadiusKm = 6371.0;
        double deltaLatitude = Math.toRadians(latitude2 - latitude1);
        double deltaLongitude = Math.toRadians(longitude2 - longitude1);

        double a = Math.sin(deltaLatitude / 2) * Math.sin(deltaLatitude / 2)
                + Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2))
                * Math.sin(deltaLongitude / 2) * Math.sin(deltaLongitude / 2);
        // En ciertas latitudes el error de coma flotante hace que 'a' supere 1.0,
        // lo que deja Math.sqrt(1 - a) en NaN y por tanto la distancia en NaN
        // (y NaN > umbral es siempre false, asi que el salto mas grande posible
        // en la Tierra pasaba desapercibido). Se acota 'a' a 1.0 antes de la raiz.
        double c = 2 * Math.asin(Math.min(1.0, Math.sqrt(a)));
        return earthRadiusKm * c;
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}