package com.ingesta.repository;

import com.ingesta.model.DatosDocumento;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryDatosDocumentoRepository implements DatosDocumentoRepository {

    private final Map<String, DatosDocumento> store = new ConcurrentHashMap<>();

    @Override
    public void save(DatosDocumento datos) {
        store.put(datos.transactionId(), datos);
    }

    @Override
    public Optional<DatosDocumento> findByTransactionId(String transactionId) {
        return Optional.ofNullable(store.get(transactionId));
    }
}
