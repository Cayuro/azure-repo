#!/usr/bin/env bash
#
# Envia 3 transacciones a la API para disparar un caso de fraude y, con el, el correo.
# Prueba la cadena COMPLETA: POST -> scoring -> cola -> Function -> ACS -> correo.
#
#   bash test-sh/02-enviar-transacciones.sh
#   API=http://appcentinelaprodgrupo3.azurewebsites.net bash test-sh/02-enviar-transacciones.sh
#
# Requiere la app corriendo (ver test-sh/README.md). ENVIA UN CORREO REAL.

source "$(dirname "$0")/comun.sh"

CUENTA="acc-e2e-$(date +%H%M%S)"

# Las tres van seguidas a proposito. El motor usa ingestedAt (= Instant.now() al ingestar),
# no occurredAt, asi que enviarlas de golpe garantiza VELOCIDAD (3 en la ventana de 3 min)
# y GEO_IMPOSIBLE (Bogota -> Madrid en segundos). La tercera anade MONTO_ATIPICO (1000 es
# mas de 5x el promedio de 100) y COMERCIO_RIESGO (gambling).
#   35 + 30 + 17 + 20 = 102 > 60  ->  se abre caso y se encola la alerta.

postear() {
  local n=$1 monto=$2 lat=$3 lon=$4 categoria=$5
  local id="${CUENTA}-tx-${n}"

  local cuerpo
  cuerpo=$(python3 - "$id" "$CUENTA" "$monto" "$lat" "$lon" "$categoria" <<'PY'
import json, sys
_, tid, cuenta, monto, lat, lon, categoria = sys.argv
print(json.dumps({
    "transactionId": tid,
    "accountId": cuenta,
    "amount": float(monto),
    "currency": "COP",
    "latitude": float(lat),
    "longitude": float(lon),
    "merchantId": "m-e2e",
    "merchantCategory": categoria,
}, separators=(",", ":")))
PY
)

  printf '  %-28s %8s %-9s -> ' "$id" "$monto" "$categoria"
  curl -sS -X POST "$API/api/v1/transactions" \
       -H 'Content-Type: application/json' \
       -d "$cuerpo" \
       -o /dev/null -w 'HTTP %{http_code}\n' \
    || { echo; echo "La API no responde en $API. Levanta la app: ver test-sh/README.md"; exit 1; }
}

titulo "3 transacciones a $API (cuenta $CUENTA)"
postear 1 100.00   4.7110 -74.0721 retail
postear 2 100.00   4.7111 -74.0722 retail
postear 3 1000.00 40.4168  -3.7038 gambling

echo
echo "Esperado: 202 en las tres (200 = ya recibida, 429 = rate limit)."
echo
echo "En los logs de la app debe aparecer:"
echo "  Alerta de fraude encolada para notificacion por correo. transactionId=${CUENTA}-tx-3 score=102"
echo
echo "Y el riesgo calculado:"
echo "  curl -s $API/api/v1/transactions/${CUENTA}-tx-3/riesgo | jq"
echo
echo "  Trazas de la Function:  bash test-sh/03-logs-function.sh"
