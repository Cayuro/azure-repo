#!/usr/bin/env bash
# =============================================================
# Encola samples/fraud-alert-event-sample.json en la cola de fraude, para
# probar la Function sin depender del App Service real.
#
# Se hizo como script (en vez de un comando largo pegado en la terminal)
# porque pegar el "--content $(cat ruta/muy/larga.json)" completo en una
# sola linea es propenso a que la terminal inserte saltos de linea reales
# al hacer wrap, corrompiendo el JSON (o incluso el path del archivo).
# =============================================================
set -euo pipefail

STORAGE_ACCOUNT="${STORAGE_ACCOUNT:?export STORAGE_ACCOUNT=<storage-account>}"
QUEUE_NAME="${QUEUE_NAME:-cola-casos-fraude}"
SAMPLE_FILE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/fraud-alert-event-sample.json"

echo ">> Encolando $SAMPLE_FILE en $QUEUE_NAME ($STORAGE_ACCOUNT)..."
az storage message put \
  --queue-name "$QUEUE_NAME" \
  --account-name "$STORAGE_ACCOUNT" \
  --auth-mode login \
  --content "$(cat "$SAMPLE_FILE")" \
  --output none

echo "OK, mensaje encolado."
