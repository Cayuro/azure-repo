package com.ingesta.dto;

import com.ingesta.model.FraudCase;

public class CasoFraudeDetectadoEvento {

    private final String eventType;
    private final FraudCase data;

    public CasoFraudeDetectadoEvento(FraudCase data) {
        this.eventType = "CASO_FRAUDE_DETECTADO";
        this.data = data;
    }

    public String getEventType() {
        return eventType;
    }

    public FraudCase getData() {
        return data;
    }
}
