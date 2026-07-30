package com.company.emailsender.template;

import com.company.emailsender.dto.FraudAlertEvent;
import com.company.emailsender.model.RuleActivation;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class FraudEmailTemplateBuilder {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    public String buildSubject(FraudAlertEvent event) {
        return String.format(
            "Alerta de fraude - Transaccion %s (score %d/%d)",
            event.transactionId(), event.score(), event.threshold()
        );
    }

    public String buildHtml(FraudAlertEvent event) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body style=\"font-family:Arial,sans-serif;font-size:14px;color:#1f2933;\">");
        html.append("<h2 style=\"color:#b91c1c;\">Alerta de fraude detectada</h2>");

        html.append("<table cellpadding=\"6\" cellspacing=\"0\" style=\"border-collapse:collapse;\">");
        appendRow(html, "Transaccion", event.transactionId());
        appendRow(html, "Cuenta", event.accountId());
        appendRow(html, "Monto", event.amount() + " " + event.currency());
        appendRow(html, "Ocurrida", format(event.occurredAt()));
        appendRow(html, "Ingestada", format(event.ingestedAt()));
        appendRow(html, "Puntuada", format(event.scoredAt()));
        appendRow(html, "Score", event.score() + " (umbral " + event.threshold() + ")");
        appendRow(html, "Comercio", event.merchantId() + " - " + event.merchantCategory());
        if (event.latitude() != null && event.longitude() != null) {
            appendRow(html, "Ubicacion", event.latitude() + ", " + event.longitude());
        }
        html.append("</table>");

        if (event.activations() != null && !event.activations().isEmpty()) {
            html.append("<h3>Reglas activadas</h3>");
            html.append("<table cellpadding=\"6\" cellspacing=\"0\" style=\"border-collapse:collapse;border:1px solid #d1d5db;\">");
            html.append("<tr style=\"background:#f3f4f6;\"><th align=\"left\">Regla</th><th align=\"left\">Descripcion</th><th align=\"left\">Puntos</th></tr>");
            for (RuleActivation activation : event.activations()) {
                html.append("<tr>")
                    .append("<td style=\"border:1px solid #e5e7eb;\">").append(escape(activation.ruleCode())).append("</td>")
                    .append("<td style=\"border:1px solid #e5e7eb;\">").append(escape(activation.description())).append("</td>")
                    .append("<td style=\"border:1px solid #e5e7eb;\">").append(activation.points()).append("</td>")
                    .append("</tr>");
            }
            html.append("</table>");
        }

        html.append("</body></html>");
        return html.toString();
    }

    private void appendRow(StringBuilder html, String label, Object value) {
        html.append("<tr>")
            .append("<td style=\"font-weight:bold;padding-right:12px;\">").append(escape(label)).append("</td>")
            .append("<td>").append(escape(String.valueOf(value))).append("</td>")
            .append("</tr>");
    }

    private String format(java.time.Instant instant) {
        return instant == null ? "-" : TIMESTAMP_FORMATTER.format(instant);
    }

    private String escape(String value) {
        if (value == null) {
            return "-";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }
}
