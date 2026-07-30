package com.ingesta.repository;

import com.ingesta.model.FraudCaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FraudCaseJpaRepository extends JpaRepository<FraudCaseEntity, Integer> {

    @Query("SELECT c FROM FraudCaseEntity c LEFT JOIN FETCH c.status s WHERE c.transactionId = :transactionId")
    Optional<FraudCaseEntity> findByTransactionIdWithStatus(@Param("transactionId") String transactionId);
}
