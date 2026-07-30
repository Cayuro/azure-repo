package com.company.emailsender.model;

public record RuleActivation(
    String ruleCode,
    String description,
    int points
) {}
