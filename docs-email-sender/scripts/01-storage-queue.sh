#!/usr/bin/env bash
# =============================================================
# SERVICIO 1/5 - Azure Storage Account + colas de casos de fraude
#
# Crea la Storage Account y dos colas:
#   - la principal, donde el App Service de scoring publica los eventos
#   - la de "poison", donde el host de Functions mueve los mensajes que
#     fallaron maxDequeueCount veces (5, configurado en host.json)
#
# Esta MISMA cuenta la reutiliza despues la Function App como
# AzureWebJobsStorage (su plomeria interna). Ver decision 3 en
# ../01-servicios-y-arquitectura.md.
#
# Debe correrse ANTES que 04-function-app.sh.
# =============================================================
set -euo pipefail

ENV_FILE="${ENV_FILE:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/.env}"
if [ -f "$ENV_FILE" ]; then
  set -a; source "$ENV_FILE"; set +a
fi

RG="${RG:?Falta RG (definelo en docs/.env)}"
LOCATION="${LOCATION:?Falta LOCATION}"
STORAGE_ACCOUNT="${STORAGE_ACCOUNT:?Falta STORAGE_ACCOUNT}"
QUEUE_NAME="${QUEUE_NAME:-cola-casos-fraude}"
QUEUE_POISON_NAME="${QUEUE_POISON_NAME:-${QUEUE_NAME}-poison}"
STORAGE_SKU="${STORAGE_SKU:-Standard_LRS}"
PRODUCER_PRINCIPAL_ID="${PRODUCER_PRINCIPAL_ID:-}"

echo ">> Resource Group: verificando $RG..."
az group show --name "$RG" --output none

echo ">> Storage Account: creando/verificando $STORAGE_ACCOUNT..."
az storage account create \
  --name "$STORAGE_ACCOUNT" \
  --resource-group "$RG" \
  --location "$LOCATION" \
  --sku "$STORAGE_SKU" \
  --kind StorageV2 \
  --min-tls-version TLS1_2 \
  --https-only true \
  --allow-blob-public-access false \
  --output none

echo ">> Cola principal: creando/verificando $QUEUE_NAME..."
az storage queue create \
  --account-name "$STORAGE_ACCOUNT" \
  --name "$QUEUE_NAME" \
  --auth-mode login \
  --output none

echo ">> Cola de poison: creando/verificando $QUEUE_POISON_NAME..."
az storage queue create \
  --account-name "$STORAGE_ACCOUNT" \
  --name "$QUEUE_POISON_NAME" \
  --auth-mode login \
  --output none

STORAGE_ID=$(az storage account show --name "$STORAGE_ACCOUNT" --resource-group "$RG" --query id -o tsv)

# RBAC del productor (App Service de scoring). El de la Function se asigna en
# 04-function-app.sh, porque hasta entonces esa identidad no existe.
if [ -n "$PRODUCER_PRINCIPAL_ID" ]; then
  echo ">> RBAC: 'Storage Queue Data Contributor' para el productor ($PRODUCER_PRINCIPAL_ID)..."
  if ! ROLE_ERR=$(az role assignment create \
      --assignee "$PRODUCER_PRINCIPAL_ID" \
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
else
  echo "!! PRODUCER_PRINCIPAL_ID vacio: el App Service de scoring no podra publicar"
  echo "   hasta que se le asigne 'Storage Queue Data Contributor' sobre esta cuenta."
fi

echo ""
echo "==================== RESUMEN ===================="
echo "Storage Account : $STORAGE_ACCOUNT ($LOCATION)"
echo "Cola principal  : $QUEUE_NAME"
echo "Cola poison     : $QUEUE_POISON_NAME"
echo ""
echo "Siguiente paso: bash docs/scripts/02-communication-services.sh"
echo "================================================="
