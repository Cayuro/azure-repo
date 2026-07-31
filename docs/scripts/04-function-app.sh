#!/usr/bin/env bash
# =============================================================
# SERVICIO 4/5 - Function App (Java 21) que envia los correos
#
# Crea la Function App sobre el plan dedicado, le activa Managed Identity y
# le da permiso de LECTURA/ESCRITURA sobre la cola sin usar claves.
#
# Requiere que ya existan:
#   - la Storage Account (01-storage-queue.sh): se usa como AzureWebJobsStorage,
#     y `az functionapp create` exige que exista de antemano
#   - el App Service Plan (03-app-service-plan.sh)
#
# Despues de este script hay que:
#   1. bash docs/scripts/05-app-settings.sh   (configuracion de la app)
#   2. cd emailSender/email-sender-function && mvn clean package && mvn azure-functions:deploy
# =============================================================
set -euo pipefail

ENV_FILE="${ENV_FILE:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/.env}"
if [ -f "$ENV_FILE" ]; then
  set -a; source "$ENV_FILE"; set +a
fi

RG="${RG:?Falta RG (definelo en docs/.env)}"
FUNCTION_APP_NAME="${FUNCTION_APP_NAME:?Falta FUNCTION_APP_NAME}"
STORAGE_ACCOUNT="${STORAGE_ACCOUNT:?Falta STORAGE_ACCOUNT}"
PLAN_NAME="${PLAN_NAME:?Falta PLAN_NAME}"

echo ">> Resource Group: verificando $RG..."
az group show --name "$RG" --output none

echo ">> Verificando que exista la Storage Account $STORAGE_ACCOUNT (script 01)..."
az storage account show --name "$STORAGE_ACCOUNT" --resource-group "$RG" --output none

echo ">> Verificando que exista el App Service Plan $PLAN_NAME (script 03)..."
az appservice plan show --name "$PLAN_NAME" --resource-group "$RG" --output none

# Ojo con --runtime-version: az cli exige "21.0", rechaza "21".
echo ">> Function App: creando/verificando $FUNCTION_APP_NAME..."
az functionapp create \
  --resource-group "$RG" \
  --name "$FUNCTION_APP_NAME" \
  --storage-account "$STORAGE_ACCOUNT" \
  --plan "$PLAN_NAME" \
  --runtime java \
  --runtime-version 21.0 \
  --os-type Linux \
  --functions-version 4 \
  --output none

echo ">> Activando Managed Identity..."
az functionapp identity assign \
  --name "$FUNCTION_APP_NAME" \
  --resource-group "$RG" \
  --output none

PRINCIPAL_ID=$(az functionapp identity show --name "$FUNCTION_APP_NAME" --resource-group "$RG" --query principalId -o tsv)
STORAGE_ID=$(az storage account show --name "$STORAGE_ACCOUNT" --resource-group "$RG" --query id -o tsv)

echo ">> RBAC: 'Storage Queue Data Contributor' para la Function ($PRINCIPAL_ID)..."
if ! ROLE_ERR=$(az role assignment create \
    --assignee "$PRINCIPAL_ID" \
    --role "Storage Queue Data Contributor" \
    --scope "$STORAGE_ID" \
    --output none 2>&1); then
  if echo "$ROLE_ERR" | grep -qi "RoleAssignmentExists"; then
    echo "   (ya estaba asignado)"
  else
    echo "$ROLE_ERR" >&2
    exit 1
  fi
fi

# Sin esto, en un plan Dedicated el sitio se puede dormir por inactividad y
# el listener de la cola (que no es HTTP) deja de escuchar.
echo ">> Activando Always On..."
az functionapp config set \
  --name "$FUNCTION_APP_NAME" \
  --resource-group "$RG" \
  --always-on true \
  --output none

# Necesario para que `az webapp log tail` muestre trazas de la aplicacion y
# no solo eventos de la plataforma.
echo ">> Habilitando logging de aplicacion..."
az webapp log config \
  --name "$FUNCTION_APP_NAME" \
  --resource-group "$RG" \
  --application-logging filesystem \
  --level information \
  --output none

echo ""
echo "==================== RESUMEN ===================="
echo "Function App     : $FUNCTION_APP_NAME (plan $PLAN_NAME)"
echo "Storage Account  : $STORAGE_ACCOUNT (AzureWebJobsStorage + cola de negocio)"
echo "Managed Identity : $PRINCIPAL_ID (con RBAC sobre la cola)"
echo "Always On        : activado"
echo ""
echo "Siguiente paso: bash docs/scripts/05-app-settings.sh"
echo "Y luego desplegar el codigo:"
echo "  cd emailSender/email-sender-function && mvn clean package && mvn azure-functions:deploy"
echo "================================================="
