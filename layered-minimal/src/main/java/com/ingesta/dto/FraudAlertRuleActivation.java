package com.ingesta.dto;

import com.ingesta.model.RuleActivation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Regla activada, en el formato que espera la Azure Function que envia los correos
 * (record {@code com.company.emailsender.model.RuleActivation}).
 *
 * El modelo interno ({@link RuleActivation}) guarda los datos de la activacion como una
 * lista de pares {@code clave=valor} pensada para el dashboard; la Function en cambio
 * espera una sola frase legible, porque va tal cual a una celda de la tabla del correo
 * que lee el analista. Esta clase hace esa traduccion.
 */
public record FraudAlertRuleActivation(
        String ruleCode,
        String description,
        int points
) {

    public static FraudAlertRuleActivation from(RuleActivation activation) {
        return new FraudAlertRuleActivation(
                activation.ruleId(),
                describir(activation.ruleId(), activation.details()),
                activation.points());
    }

    private static String describir(String ruleId, List<String> details) {
        Map<String, String> valores = indexar(details);

        String descripcion = switch (ruleId) {
            case "VELOCIDAD" -> formatear(valores,
                    "%s transacciones en una ventana de %s minutos",
                    "transactionsInWindow", "windowMinutes");
            case "MONTO_ATIPICO" -> formatear(valores,
                    "Monto %s, mas de %s veces el promedio historico de %s",
                    "amount", "multiplier", "historicalAverage");
            case "GEO_IMPOSIBLE" -> formatear(valores,
                    "%s km en %s segundos (%s km/h)",
                    "distanceKm", "elapsedSeconds", "speedKmH");
            case "COMERCIO_RIESGO" -> formatear(valores,
                    "Categoria de alto riesgo: %s (comercio %s)",
                    "merchantCategory", "merchantId");
            default -> null;
        };

        // Regla desconocida o detalles con claves distintas a las esperadas: se manda el
        // contenido crudo antes que una frase con huecos. El correo pierde estilo, no datos.
        return descripcion != null ? descripcion : String.join(", ", details);
    }

    private static Map<String, String> indexar(List<String> details) {
        Map<String, String> valores = new LinkedHashMap<>();
        for (String detail : details) {
            int separador = detail.indexOf('=');
            if (separador > 0) {
                valores.put(detail.substring(0, separador), detail.substring(separador + 1));
            }
        }
        return valores;
    }

    private static String formatear(Map<String, String> valores, String plantilla, String... claves) {
        Object[] argumentos = new Object[claves.length];
        for (int i = 0; i < claves.length; i++) {
            String valor = valores.get(claves[i]);
            if (valor == null) {
                return null;
            }
            argumentos[i] = valor;
        }
        return plantilla.formatted(argumentos);
    }
}
