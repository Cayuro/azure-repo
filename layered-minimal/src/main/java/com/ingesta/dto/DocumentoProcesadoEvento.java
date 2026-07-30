package com.ingesta.dto;

import com.ingesta.model.DatosDocumento;

public class DocumentoProcesadoEvento {

    private final String eventType;
    private final DatosDocumento data;

    public DocumentoProcesadoEvento(DatosDocumento data) {
        this.eventType = "DOCUMENTO_PROCESADO";
        this.data = data;
    }

    public String getEventType() {
        return eventType;
    }

    public DatosDocumento getData() {
        return data;
    }
}
