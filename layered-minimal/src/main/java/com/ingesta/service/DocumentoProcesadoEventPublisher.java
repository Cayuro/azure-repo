package com.ingesta.service;

import com.ingesta.model.DatosDocumento;

public interface DocumentoProcesadoEventPublisher {

    void notificarResultado(DatosDocumento datos);
}
