package com.ingesta.service;

import com.ingesta.model.CasoFraude;

public interface FraudCaseEventPublisher {

    void publicarCasoFraude(CasoFraude caso);
}
