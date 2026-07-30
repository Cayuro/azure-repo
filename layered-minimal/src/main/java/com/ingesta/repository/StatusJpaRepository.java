package com.ingesta.repository;

import com.ingesta.model.StatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StatusJpaRepository extends JpaRepository<StatusEntity, Integer> {
    Optional<StatusEntity> findByName(String name);
}
