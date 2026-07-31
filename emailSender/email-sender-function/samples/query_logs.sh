#!/usr/bin/env bash
# =============================================================
# Consulta los logs de la Function en Application Insights, filtrando el
# ruido del SDK (cada request/response HTTP a Storage) para dejar solo lo
# relevante: arranque del host, invocaciones, y errores.
#
# Uso:
#   bash samples/query_logs.sh          # ultimos 20 min
#   MINUTES=60 bash samples/query_logs.sh
#
# Se hizo como script porque la consulta KQL es larga y el terminal suele
# partirla al pegarla (y el '!' de KQL choca con la expansion de historial
# de bash, por eso aqui se usa not(...) y comillas simples).
# =============================================================
set -euo pipefail

RG="${RG:?export RG=<resource-group>}"
FUNCTION_APP_NAME="${FUNCTION_APP_NAME:?export FUNCTION_APP_NAME=<nombre-function-app>}"
MINUTES="${MINUTES:-20}"

QUERY='traces
| where timestamp > ago('"${MINUTES}"'m)
| where not(message startswith "Request [")
| where not(message startswith "Response [")
| where not(message startswith "Loading customer file")
| where not(message startswith "Loading worker file")
| project timestamp, severityLevel, message
| order by timestamp desc
| take 60'

echo ">> Logs de $FUNCTION_APP_NAME (ultimos ${MINUTES} min, sin ruido HTTP del SDK)..."
az monitor app-insights query \
  --app "$FUNCTION_APP_NAME" \
  --resource-group "$RG" \
  --analytics-query "$QUERY" \
  --query "tables[0].rows" \
  -o tsv
