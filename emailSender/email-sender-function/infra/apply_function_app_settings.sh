#!/usr/bin/env bash
# =============================================================
# Sube los App Settings del Function App en Azure a partir de tu
# local.settings.json local, sin que tengas que copiar/pegar secretos a
# mano en un comando.
#
# Omite:
#  - AzureWebJobsStorage / FUNCTIONS_WORKER_RUNTIME: ya los configura
#    "az functionapp create" solo.
#  - FraudQueueStorage (el valor plano, ej. "UseDevelopmentStorage=true"):
#    es SOLO para pruebas locales con Azurite. Si se sube tal cual a Azure,
#    tiene prioridad sobre las variables identity-based de abajo y rompe la
#    conexion real (el host de Functions usa la clave "FraudQueueStorage"
#    completa si existe, ignorando las "__" descompuestas).
#
# Si pasas STORAGE_ACCOUNT, en su lugar configura la conexion sin claves
# (Managed Identity) hacia esa cuenta:
#   FraudQueueStorage__queueServiceUri
#   FraudQueueStorage__credential=managedidentity
#
# Requiere python3 (viene por defecto en casi cualquier Linux/Mac).
#
# Generico: solo cambia las variables de abajo.
# =============================================================
set -euo pipefail

RG="${RG:?export RG=<resource-group>}"
FUNCTION_APP_NAME="${FUNCTION_APP_NAME:?export FUNCTION_APP_NAME=<nombre-function-app>}"
LOCAL_SETTINGS="${LOCAL_SETTINGS:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/local.settings.json}"
STORAGE_ACCOUNT="${STORAGE_ACCOUNT:-}"   # opcional: si se pasa, configura FraudQueueStorage por Managed Identity

if [ ! -f "$LOCAL_SETTINGS" ]; then
  echo "No se encontro $LOCAL_SETTINGS (ajusta LOCAL_SETTINGS si esta en otro lado)."
  exit 1
fi

echo ">> Leyendo valores de $LOCAL_SETTINGS..."
mapfile -d '' -t SETTINGS < <(python3 - "$LOCAL_SETTINGS" <<'EOF'
import json, sys
data = json.load(open(sys.argv[1]))
skip = {"AzureWebJobsStorage", "FUNCTIONS_WORKER_RUNTIME", "FraudQueueStorage"}
for k, v in data["Values"].items():
    if k in skip or str(v).startswith("<"):
        continue
    sys.stdout.write(f"{k}={v}\0")
EOF
)

if [ -n "$STORAGE_ACCOUNT" ]; then
  SETTINGS+=("FraudQueueStorage__queueServiceUri=https://${STORAGE_ACCOUNT}.queue.core.windows.net")
  SETTINGS+=("FraudQueueStorage__credential=managedidentity")
else
  echo "!! STORAGE_ACCOUNT vacio: no se configura FraudQueueStorage (identity-based)."
  echo "   Sin esto, el Queue Trigger no va a poder conectarse a la cola en Azure."
fi

if [ "${#SETTINGS[@]}" -eq 0 ]; then
  echo "No hay valores validos para subir (revisa que local.settings.json este completo,"
  echo "sin placeholders tipo <...> sin rellenar)."
  exit 1
fi

echo ">> Subiendo ${#SETTINGS[@]} App Settings a $FUNCTION_APP_NAME..."
az functionapp config appsettings set \
  --name "$FUNCTION_APP_NAME" \
  --resource-group "$RG" \
  --settings "${SETTINGS[@]}" \
  --output none

echo "OK. App Settings aplicados (no se imprimen aqui por seguridad)."
