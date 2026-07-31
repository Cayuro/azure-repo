package com.ingesta.testsupport;

import com.ingesta.model.Transaction;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Constructor fluido de transacciones para los tests.
 *
 * Existe para que cada caso de prueba declare unicamente los campos que le
 * importan (el monto en las reglas de monto, las coordenadas en las de geo)
 * y no tenga que repetir los diez argumentos del record en cada linea.
 */
public final class TransaccionFixture {

    // Valores neutros: no activan ninguna regla de scoring por si solos.
    public static final Instant T0 = Instant.parse("2026-07-23T12:00:00Z");
    private static final BigDecimal MONTO_NEUTRO = new BigDecimal("100.00");
    private static final String CATEGORIA_NEUTRA = "retail";

    private String transactionId = "tx-1";
    private String accountId = "acc-1";
    private BigDecimal amount = MONTO_NEUTRO;
    private String currency = "USD";
    private Instant occurredAt = T0;
    private Instant ingestedAt = T0;
    private Double latitude = 4.7110;
    private Double longitude = -74.0721;
    private String merchantId = "mer-1";
    private String merchantCategory = CATEGORIA_NEUTRA;

    private TransaccionFixture() {
    }

    public static TransaccionFixture una() {
        return new TransaccionFixture();
    }

    public TransaccionFixture conId(String value) {
        this.transactionId = value;
        return this;
    }

    public TransaccionFixture conCuenta(String value) {
        this.accountId = value;
        return this;
    }

    public TransaccionFixture conMonto(String value) {
        this.amount = new BigDecimal(value);
        return this;
    }

    public TransaccionFixture conMonto(BigDecimal value) {
        this.amount = value;
        return this;
    }

    public TransaccionFixture conMoneda(String value) {
        this.currency = value;
        return this;
    }

    /** Fija occurredAt e ingestedAt al mismo instante, que es el caso habitual. */
    public TransaccionFixture en(Instant value) {
        this.occurredAt = value;
        this.ingestedAt = value;
        return this;
    }

    /** Desplaza el instante respecto de {@link #T0}, para construir secuencias temporales. */
    public TransaccionFixture enT0MasSegundos(long segundos) {
        return en(T0.plusSeconds(segundos));
    }

    public TransaccionFixture conOccurredAt(Instant value) {
        this.occurredAt = value;
        return this;
    }

    public TransaccionFixture conIngestedAt(Instant value) {
        this.ingestedAt = value;
        return this;
    }

    public TransaccionFixture en(Double latitud, Double longitud) {
        this.latitude = latitud;
        this.longitude = longitud;
        return this;
    }

    public TransaccionFixture conComercio(String value) {
        this.merchantId = value;
        return this;
    }

    public TransaccionFixture conCategoria(String value) {
        this.merchantCategory = value;
        return this;
    }

    public Transaction construir() {
        return new Transaction(
                transactionId, accountId, amount, currency,
                occurredAt, ingestedAt, latitude, longitude,
                merchantId, merchantCategory);
    }
}
