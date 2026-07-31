#!/usr/bin/env bash
#
# Encola una alerta directamente en cola-casos-fraude, saltandose la app de scoring.
# Prueba la mitad derecha de la cadena: cola -> Function -> ACS -> correo.
#
#   bash test-sh/01-encolar-alerta.sh
#
# ENVIA UN CORREO REAL a los destinatarios de FraudAlertRecipients.
#
# El JSON lo construye python3 en una sola linea y lo valida antes de enviarlo. Escribir
# el JSON a mano en la terminal es justo lo que rompe esto: al pegar un comando largo, la
# terminal mete saltos de linea reales dentro de los strings y la Function falla con
# "Illegal unquoted character ((CTRL-CHAR, code 10))". Aqui eso no puede pasar.

source "$(dirname "$0")/comun.sh"
comprobar_sesion

MARCA="tx-e2e-$(date +%H%M%S)"

JSON=$(python3 - "$MARCA" <<'PY'
import json, sys
from datetime import datetime, timezone

ahora = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

# Mismos campos, nombres y tipos que produce FraudAlertEvent.of(...) en el App Service,
# que a su vez son los del record FraudAlertEvent de la Function. Un campo de mas hace
# fallar el mensaje entero (la Function deserializa con Jackson por defecto).
evento = {
    "transactionId": sys.argv[1],
    "accountId": "acc-e2e",
    "amount": 1000.00,
    "currency": "COP",
    "occurredAt": ahora,
    "ingestedAt": ahora,
    "latitude": 40.4168,
    "longitude": -3.7038,
    "merchantId": "m-e2e",
    "merchantCategory": "gambling",
    "score": 102,
    "threshold": 60,
    "scoredAt": ahora,
    "activations": [
        {"ruleCode": "VELOCIDAD",
         "description": "3 transacciones en una ventana de 3 minutos", "points": 35},
        {"ruleCode": "MONTO_ATIPICO",
         "description": "Monto 1000.00, mas de 5.0 veces el promedio historico de 100.00", "points": 30},
        {"ruleCode": "GEO_IMPOSIBLE",
         "description": "8033.45 km en 1 segundos (2.89E7 km/h)", "points": 17},
        {"ruleCode": "COMERCIO_RIESGO",
         "description": "Categoria de alto riesgo: gambling (comercio m-e2e)", "points": 20},
    ],
}
print(json.dumps(evento, separators=(",", ":")))
PY
)

titulo "Encolando $MARCA en $QUEUE ($STORAGE_ACCOUNT)"
echo "$JSON" | cut -c1-140
echo "  ... ($(echo -n "$JSON" | wc -c) bytes, una sola linea)"

az storage message put \
  --queue-name "$QUEUE" \
  --account-name "$STORAGE_ACCOUNT" \
  --auth-mode login \
  --content "$JSON" \
  --output none

echo
echo "Encolado. La Function sondea cada ~2 segundos."
echo "  Revisa la bandeja de entrada (y spam: el dominio *.azurecomm.net es nuevo)."
echo "  Trazas:  bash test-sh/03-logs-function.sh"
echo "  Si fallo: bash test-sh/04-poison.sh"
