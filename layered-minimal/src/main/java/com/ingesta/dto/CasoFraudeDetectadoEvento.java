package com.ingesta.dto;

import com.ingesta.model.CasoFraude;

public class CasoFraudeDetectadoEvento {

    private final String eventType;
    private final CasoFraude data;

    public CasoFraudeDetectadoEvento(CasoFraude data) {
        this.eventType = "CASO_FRAUDE_DETECTADO";
        this.data = data;
    }

    public String getEventType() {
        return eventType;
    }

    public CasoFraude getData() {
        return data;
    }
}
