package com.ingesta.repository;

import com.ingesta.model.FraudCase;
import com.ingesta.model.FraudCaseEntity;
import com.ingesta.model.StatusEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Profile("!local")
public class JpaFraudCaseRepository implements FraudCaseRepository {

    private final FraudCaseJpaRepository fraudCaseJpaRepository;
    private final StatusJpaRepository statusJpaRepository;

    public JpaFraudCaseRepository(FraudCaseJpaRepository fraudCaseJpaRepository,
                                  StatusJpaRepository statusJpaRepository) {
        this.fraudCaseJpaRepository = fraudCaseJpaRepository;
        this.statusJpaRepository = statusJpaRepository;
    }

    @Override
    public void save(FraudCase fraudCase) {
        FraudCaseEntity entity = fraudCaseJpaRepository.findByTransactionIdWithStatus(fraudCase.transactionId())
                .orElseGet(() -> {
                    StatusEntity status = statusJpaRepository.findByName(fraudCase.status())
                            .orElseGet(() -> statusJpaRepository.save(new StatusEntity(fraudCase.status())));
                    return new FraudCaseEntity(status, fraudCase.transactionId(), fraudCase.score(), fraudCase.openedAt());
                });

        StatusEntity status = statusJpaRepository.findByName(fraudCase.status())
                .orElseGet(() -> statusJpaRepository.save(new StatusEntity(fraudCase.status())));

        entity.setStatus(status);
        entity.setScore(fraudCase.score());
        entity.setOpenedAt(fraudCase.openedAt());

        fraudCaseJpaRepository.save(entity);
    }

    @Override
    public Optional<FraudCase> findByTransactionId(String transactionId) {
        return fraudCaseJpaRepository.findByTransactionIdWithStatus(transactionId)
                .map(this::toDomain);
    }

    private FraudCase toDomain(FraudCaseEntity entity) {
        return new FraudCase(
                String.valueOf(entity.getIdCase()),
                entity.getTransactionId(),
                entity.getScore() != null ? entity.getScore() : 0,
                entity.getStatus().getName(),
                entity.getOpenedAt(),
                java.util.List.of()
        );
    }
}
