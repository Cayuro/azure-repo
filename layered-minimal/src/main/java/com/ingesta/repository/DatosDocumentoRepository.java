package com.ingesta.repository;

import com.ingesta.model.DatosDocumento;

import java.util.Optional;

public interface DatosDocumentoRepository {

    void save(DatosDocumento datos);

    Optional<DatosDocumento> findByTransactionId(String transactionId);
}
