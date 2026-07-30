#!/usr/bin/env bash
# =============================================================
# Function App (variante SIN VNet) - plan Consumption (Y1)
#
# Usa la MISMA storage account que ya tiene la cola de fraude (creada por
# provision_storage_queue.sh) tanto para AzureWebJobsStorage (plomeria
# interna del runtime: paquete de deployment, leases de coordinacion) como
# para la cola de negocio. En esta variante no hace falta separarlas: como
# nada queda bloqueado a nivel de red, no existe el problema de "no puedo
# desplegar el paquete a una cuenta sin acceso publico" que si obliga a
# usar dos cuentas en la variante con-vnet.
#
# Requiere que STORAGE_ACCOUNT ya exista -> correr primero
# provision_storage_queue.sh.
#
# Generico: solo cambia las variables de abajo.
# =============================================================
set -euo pipefail

RG="${RG:?export RG=<resource-group>}"
LOCATION="${LOCATION:?export LOCATION=<region>}"
FUNCTION_APP_NAME="${FUNCTION_APP_NAME:?export FUNCTION_APP_NAME=<nombre-function-app>}"
STORAGE_ACCOUNT="${STORAGE_ACCOUNT:?export STORAGE_ACCOUNT=<storage-account-creada-por-provision_storage_queue.sh>}"

echo ">> Resource Group: verificando $RG..."
az group show --name "$RG" --output none

echo ">> Verificando que $STORAGE_ACCOUNT ya exista (la crea provision_storage_queue.sh)..."
az storage account show --name "$STORAGE_ACCOUNT" --resource-group "$RG" --output none

echo ">> Function App (Consumption): creando/verificando $FUNCTION_APP_NAME..."
az functionapp create \
  --resource-group "$RG" \
  --name "$FUNCTION_APP_NAME" \
  --storage-account "$STORAGE_ACCOUNT" \
  --consumption-plan-location "$LOCATION" \
  --runtime java \
  --runtime-version 21 \
  --os-type Linux \
  --functions-version 4 \
  --output none 2>/dev/null || echo "   (ya existia)"

echo ">> Activando Managed Identity..."
az functionapp identity assign \
  --name "$FUNCTION_APP_NAME" \
  --resource-group "$RG" \
  --output none

PRINCIPAL_ID=$(az functionapp identity show --name "$FUNCTION_APP_NAME" --resource-group "$RG" --query principalId -o tsv)
STORAGE_ID=$(az storage account show --name "$STORAGE_ACCOUNT" --resource-group "$RG" --query id -o tsv)

echo ">> RBAC: 'Storage Queue Data Contributor' para la Function sobre $STORAGE_ACCOUNT..."
az role assignment create \
  --assignee "$PRINCIPAL_ID" \
  --role "Storage Queue Data Contributor" \
  --scope "$STORAGE_ID" \
  --output none 2>/dev/null || true

echo ""
echo "==================== RESUMEN ===================="
echo "Function App     : $FUNCTION_APP_NAME (Consumption / Y1)"
echo "Storage Account  : $STORAGE_ACCOUNT (compartida: AzureWebJobsStorage + cola de negocio)"
echo "Managed Identity : $PRINCIPAL_ID (RBAC Queue Data Contributor ya asignado)"
echo ""
echo "App settings pendientes en el Function App:"
echo "  FraudQueueStorage__queueServiceUri=https://${STORAGE_ACCOUNT}.queue.core.windows.net"
echo "  FraudQueueStorage__credential=managedidentity"
echo "==================================================="
