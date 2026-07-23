package com.ingesta.model;

import java.util.List;

public record RuleActivation(
        String ruleId,
        int points,
        List<String> details
) {
    public RuleActivation {
        details = List.copyOf(details);
    }
}