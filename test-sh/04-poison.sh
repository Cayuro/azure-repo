#!/usr/bin/env bash
#
# Mensajes que fallaron 5 veces y el motivo. Primer sitio donde mirar cuando "no llego
# el correo".
#
#   bash test-sh/04-poison.sh            # ver contenido y ultimas excepciones
#   bash test-sh/04-poison.sh --vaciar   # borrar todo (pide confirmacion)
#
# Ver el contenido exacto del mensaje que fallo suele bastar: JSON mal formado, fecha
# numerica en vez de ISO-8601, o un campo que la Function no conoce.

source "$(dirname "$0")/comun.sh"
comprobar_sesion

if [ "${1:-}" = "--vaciar" ]; then
  titulo "Vaciar $QUEUE_POISON"
  read -r -p "Borra TODOS los mensajes fallidos, sin vuelta atras. Escribe 'si': " respuesta
  [ "$respuesta" = "si" ] || { echo "Cancelado."; exit 0; }
  az storage queue clear --name "$QUEUE_POISON" --account-name "$STORAGE_ACCOUNT" \
    --auth-mode login --output none
  echo "Vaciada."
  exit 0
fi

titulo "Contenido de $QUEUE_POISON"
az storage message peek \
  --queue-name "$QUEUE_POISON" \
  --account-name "$STORAGE_ACCOUNT" \
  --auth-mode login \
  --num-messages 32 \
  --query "[].{insertado:insertionTime, contenido:content}" \
  -o json 2>/dev/null \
  | jq -r 'if length == 0 then "  (vacia)"
           else .[] | "  \(.insertado)\n    \(.contenido[0:200])\n" end' \
  || echo "  (vacia)"

titulo "Ultimas excepciones de la Function (24h)"
az monitor app-insights query --app "$APPINSIGHTS_ID" --analytics-query "
  exceptions
  | where timestamp > ago(24h)
  | project timestamp, innermostMessage
  | order by timestamp desc
  | take 5" \
  --query "tables[0].rows" -o tsv 2>/dev/null | cut -c1-300 || echo "  (ninguna)"

cat <<'PISTAS'

Pistas rapidas:
  "Illegal unquoted character (CTRL-CHAR, code 10)"  -> salto de linea dentro de un
      string del JSON. Pasa al pegar comandos largos en la terminal; por eso los
      scripts de esta carpeta construyen el JSON con python3 en una sola linea.
  "Unrecognized field"      -> campo de mas: hay que agregarlo antes al record
                               FraudAlertEvent de la Function.
  "Message decoding failed" -> el mensaje llego en Base64. Debe ir en texto plano.
PISTAS
