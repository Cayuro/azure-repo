package com.ingesta.repository.cosmos;

import com.azure.spring.data.cosmos.core.mapping.Container;
import com.azure.spring.data.cosmos.core.mapping.PartitionKey;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ingesta.model.RuleActivation;
import com.ingesta.model.TransactionScore;
import org.springframework.data.annotation.Id;

import java.time.Instant;
import java.util.List;

/**
 * Entidad persistida en el contenedor "scores" de Cosmos DB.
 * Partition key: transactionId — un score existe por transacción.
 * Las activaciones de reglas se guardan serializadas como JSON string
 * dado que Cosmos DB soporta documentos anidados pero Spring Data Cosmos
 * los maneja mejor como campo embebido cuando la lista tiene tipos polimórficos.
 */
@Container(containerName = "scores")
public class TransactionScoreEntity {

    @Id
    @PartitionKey
    private String transactionId;

    private int score;
    private int threshold;
    private Instant scoredAt;

    /**
     * Lista de RuleActivation serializada como JSON.
     * Alternativa segura a @CosmosEmbedded para tipos con listas de strings.
     */
    private String activationsJson;

    // ObjectMapper estático compartido — thread-safe para lectura/escritura
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // Constructor vacío requerido por el SDK de Cosmos DB
    public TransactionScoreEntity() {}

    public static TransactionScoreEntity from(TransactionScore s) {
        TransactionScoreEntity e = new TransactionScoreEntity();
        e.transactionId = s.transactionId();
        e.score         = s.score();
        e.threshold     = s.threshold();
        e.scoredAt      = s.scoredAt();
        try {
            e.activationsJson = MAPPER.writeValueAsString(s.activations());
        } catch (Exception ex) {
            e.activationsJson = "[]";
        }
        return e;
    }

    public TransactionScore toDomain() {
        List<RuleActivation> activations;
        try {
            activations = MAPPER.readValue(activationsJson, new TypeReference<>() {});
        } catch (Exception ex) {
            activations = List.of();
        }
        return new TransactionScore(transactionId, score, threshold, scoredAt, activations);
    }

    // Getters y setters requeridos por el SDK
    public String getTransactionId()            { return transactionId; }
    public void setTransactionId(String v)      { this.transactionId = v; }
    public int getScore()                       { return score; }
    public void setScore(int v)                 { this.score = v; }
    public int getThreshold()                   { return threshold; }
    public void setThreshold(int v)             { this.threshold = v; }
    public Instant getScoredAt()                { return scoredAt; }
    public void setScoredAt(Instant v)          { this.scoredAt = v; }
    public String getActivationsJson()          { return activationsJson; }
    public void setActivationsJson(String v)    { this.activationsJson = v; }
}
