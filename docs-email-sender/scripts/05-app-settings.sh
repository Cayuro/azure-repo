#!/usr/bin/env bash
# =============================================================
# PASO 5/5 - App Settings de la Function App
#
# Sube la configuracion de la Function a Azure leyendola de
# emailSender/email-sender-function/local.settings.json, para no tener que
# pegar el connection string de ACS a mano en la terminal (donde queda en el
# historial del shell).
#
# Omite tres claves a proposito:
#   - AzureWebJobsStorage y FUNCTIONS_WORKER_RUNTIME: las configura sola
#     `az functionapp create`.
#   - FraudQueueStorage: en local.settings.json vale "UseDevelopmentStorage=true"
#     (Azurite). Si esa clave plana se sube a Azure, TIENE PRIORIDAD sobre las
#     variables identity-based de abajo y rompe la conexion real.
#
# En su lugar configura la conexion sin claves hacia la cola:
#   FraudQueueStorage__queueServiceUri
#   FraudQueueStorage__credential=managedidentity
#
# Requiere python3.
# =============================================================
set -euo pipefail

ENV_FILE="${ENV_FILE:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/.env}"
if [ -f "$ENV_FILE" ]; then
  set -a; source "$ENV_FILE"; set +a
fi

RG="${RG:?Falta RG (definelo en docs/.env)}"
FUNCTION_APP_NAME="${FUNCTION_APP_NAME:?Falta FUNCTION_APP_NAME}"
STORAGE_ACCOUNT="${STORAGE_ACCOUNT:?Falta STORAGE_ACCOUNT}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOCAL_SETTINGS="${LOCAL_SETTINGS:-$REPO_ROOT/emailSender/email-sender-function/local.settings.json}"

if [ ! -f "$LOCAL_SETTINGS" ]; then
  echo "No se encontro $LOCAL_SETTINGS"
  echo "Creado a partir de los valores que imprimio 02-communication-services.sh."
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

SETTINGS+=("FraudQueueStorage__queueServiceUri=https://${STORAGE_ACCOUNT}.queue.core.windows.net")
SETTINGS+=("FraudQueueStorage__credential=managedidentity")

echo ">> Subiendo ${#SETTINGS[@]} App Settings a $FUNCTION_APP_NAME..."
az functionapp config appsettings set \
  --name "$FUNCTION_APP_NAME" \
  --resource-group "$RG" \
  --settings "${SETTINGS[@]}" \
  --output none

echo ""
echo "==================== RESUMEN ===================="
echo "App Settings aplicados (no se imprimen aqui por seguridad)."
echo "Verificar los nombres cargados:"
echo "  az functionapp config appsettings list --name $FUNCTION_APP_NAME -g $RG --query \"[].name\" -o tsv"
echo ""
echo "Ultimo paso, desplegar el codigo:"
echo "  cd emailSender/email-sender-function && mvn clean package && mvn azure-functions:deploy"
echo "================================================="
