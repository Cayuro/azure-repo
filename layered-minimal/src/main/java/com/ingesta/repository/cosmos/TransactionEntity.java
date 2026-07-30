package com.ingesta.repository.cosmos;

import com.azure.spring.data.cosmos.core.mapping.Container;
import com.azure.spring.data.cosmos.core.mapping.PartitionKey;
import com.ingesta.model.Transaction;
import org.springframework.data.annotation.Id;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entidad persistida en el contenedor "transacciones" de Cosmos DB.
 * Partition key: accountId — garantiza que findByAccountId sea eficiente
 * porque todas las transacciones de una cuenta están en la misma partición.
 */
@Container(containerName = "transacciones")
public class TransactionEntity {

    @Id
    private String transactionId;

    @PartitionKey
    private String accountId;

    private BigDecimal amount;
    private String currency;
    private Instant occurredAt;
    private Instant ingestedAt;
    private Double latitude;
    private Double longitude;
    private String merchantId;
    private String merchantCategory;

    // Constructor vacío requerido por el SDK de Cosmos DB
    public TransactionEntity() {}

    /**
     * Convierte el domain record Transaction en la entidad de Cosmos DB.
     */
    public static TransactionEntity from(Transaction t) {
        TransactionEntity e = new TransactionEntity();
        e.transactionId    = t.transactionId();
        e.accountId        = t.accountId();
        e.amount           = t.amount();
        e.currency         = t.currency();
        e.occurredAt       = t.occurredAt();
        e.ingestedAt       = t.ingestedAt();
        e.latitude         = t.latitude();
        e.longitude        = t.longitude();
        e.merchantId       = t.merchantId();
        e.merchantCategory = t.merchantCategory();
        return e;
    }

    /**
     * Convierte la entidad de Cosmos DB de vuelta al domain record.
     */
    public Transaction toDomain() {
        return new Transaction(
                transactionId, accountId, amount, currency,
                occurredAt, ingestedAt, latitude, longitude,
                merchantId, merchantCategory);
    }

    // Getters y setters requeridos por el SDK
    public String getTransactionId()            { return transactionId; }
    public void setTransactionId(String v)      { this.transactionId = v; }
    public String getAccountId()                { return accountId; }
    public void setAccountId(String v)          { this.accountId = v; }
    public BigDecimal getAmount()               { return amount; }
    public void setAmount(BigDecimal v)         { this.amount = v; }
    public String getCurrency()                 { return currency; }
    public void setCurrency(String v)           { this.currency = v; }
    public Instant getOccurredAt()              { return occurredAt; }
    public void setOccurredAt(Instant v)        { this.occurredAt = v; }
    public Instant getIngestedAt()              { return ingestedAt; }
    public void setIngestedAt(Instant v)        { this.ingestedAt = v; }
    public Double getLatitude()                 { return latitude; }
    public void setLatitude(Double v)           { this.latitude = v; }
    public Double getLongitude()                { return longitude; }
    public void setLongitude(Double v)          { this.longitude = v; }
    public String getMerchantId()               { return merchantId; }
    public void setMerchantId(String v)         { this.merchantId = v; }
    public String getMerchantCategory()         { return merchantCategory; }
    public void setMerchantCategory(String v)   { this.merchantCategory = v; }
}
