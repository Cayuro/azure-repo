package com.ingesta.service;

import com.ingesta.model.FraudCase;

public interface FraudCaseEventPublisher {

    void publicarCasoFraude(FraudCase caso);
}
