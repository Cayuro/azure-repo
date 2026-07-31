#!/usr/bin/env bash
#
# Trazas de la Function que envia los correos (Application Insights).
#
#   bash test-sh/03-logs-function.sh          # ultimos 30 minutos
#   bash test-sh/03-logs-function.sh 2h       # otra ventana
#
# "az webapp log tail" NO sirve aqui: solo muestra eventos de plataforma (contenedor
# arrancando/parando). Las trazas de la aplicacion viven en Application Insights.

source "$(dirname "$0")/comun.sh"
comprobar_sesion

VENTANA="${1:-30m}"

titulo "Trazas de $FUNCTION_APP (ultimos $VENTANA)"
cat <<'ESPERADO'
Un envio correcto se ve asi:
  EmailNotificationFunction invocada, deserializando mensaje...
  Procesando alerta de fraude para transaccion tx-...
  Correo de alerta de fraude enviado. transactionId=... status=...

Si NO aparece "invocada" pero si hay mensajes en poison, el fallo ocurrio antes de
llegar al codigo: encoding, permisos o binding.
ESPERADO
echo

# Se filtra el ruido HTTP del SDK de Azure (azsdk-net...), que domina el volumen y no
# aporta nada; y se unen traces con exceptions para ver el error en el mismo hilo.
az monitor app-insights query --app "$APPINSIGHTS_ID" --analytics-query "
  union traces, exceptions
  | where timestamp > ago($VENTANA)
  | extend msg = coalesce(message, innermostMessage)
  | where isnotempty(msg)
  | where msg !has 'azsdk-net' and msg !has 'x-ms-client-request-id'
  | where msg has_any ('EmailNotificationFunction','Procesando alerta','Correo de alerta',
                       'Illegal','Cannot deserialize','Unrecognized field',
                       'decoding has failed','Exception')
  | project timestamp, msg = substring(msg, 0, 260)
  | order by timestamp asc
  | take 40" \
  --query "tables[0].rows" -o tsv 2>/dev/null || true

echo
echo "(sin lineas arriba = no hubo actividad relevante en esa ventana)"
