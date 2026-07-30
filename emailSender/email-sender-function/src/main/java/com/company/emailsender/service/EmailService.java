package com.company.emailsender.service;

import com.company.emailsender.dto.FraudAlertEvent;

public interface EmailService {

    void send(FraudAlertEvent event);
}
