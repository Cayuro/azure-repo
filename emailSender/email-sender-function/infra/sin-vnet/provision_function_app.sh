#!/usr/bin/env bash
# =============================================================
# Function App (variante SIN VNet) - Consumption (Y1) o App Service Plan
# dedicado
#
# Usa la MISMA storage account que ya tiene la cola de fraude (creada por
# provision_storage_queue.sh) tanto para AzureWebJobsStorage (plomeria
# interna del runtime: paquete de deployment, leases de coordinacion) como
# para la cola de negocio. En esta variante no hace falta separarlas: como
# nada queda bloqueado a nivel de red, no existe el problema de "no puedo
# desplegar el paquete a una cuenta sin acceso publico" que si obliga a
# usar dos cuentas en la variante con-vnet.
#
# NOTA 1: el plan Consumption dinamico para Linux (Y1) no esta disponible
# en todas las regiones (ej. chilecentral no lo soporta -> error "Linux
# dynamic workers are not available in resource group"). Si tu region no lo
# soporta, exporta PLAN_NAME (y opcionalmente PLAN_SKU, default B1) y el
# script crea/usa un App Service Plan dedicado para la Function.
#
# NOTA 2: NO reutilices un App Service Plan que ya tenga otra app corriendo
# (ej. el App Service que calcula el score). Un B1 tiene ~1.75 GB de RAM;
# dos procesos Java (dos JVMs) compitiendo por eso en la misma instancia
# causa presion de memoria real y reinicios erraticos del contenedor (nos
# paso). Dale a la Function su PROPIO plan dedicado (mismo costo que subir
# de tier un plan compartido, pero sin arriesgar la otra app).
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
PLAN_NAME="${PLAN_NAME:-}"   # opcional: nombre de un App Service Plan DEDICADO a crear/usar (ver NOTA 1 y 2)
PLAN_SKU="${PLAN_SKU:-B1}"   # solo aplica si se usa PLAN_NAME

echo ">> Resource Group: verificando $RG..."
az group show --name "$RG" --output none

echo ">> Verificando que $STORAGE_ACCOUNT ya exista (la crea provision_storage_queue.sh)..."
az storage account show --name "$STORAGE_ACCOUNT" --resource-group "$RG" --output none

if [ -n "$PLAN_NAME" ]; then
  echo ">> App Service Plan: creando/verificando $PLAN_NAME ($PLAN_SKU, Linux)..."
  az appservice plan create \
    --name "$PLAN_NAME" \
    --resource-group "$RG" \
    --location "$LOCATION" \
    --sku "$PLAN_SKU" \
    --is-linux \
    --output none

  echo ">> Function App (plan '$PLAN_NAME'): creando/verificando $FUNCTION_APP_NAME..."
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
else
  echo ">> Function App (Consumption dinamico): creando/verificando $FUNCTION_APP_NAME..."
  az functionapp create \
    --resource-group "$RG" \
    --name "$FUNCTION_APP_NAME" \
    --storage-account "$STORAGE_ACCOUNT" \
    --consumption-plan-location "$LOCATION" \
    --runtime java \
    --runtime-version 21.0 \
    --os-type Linux \
    --functions-version 4 \
    --output none
fi

echo ">> Activando Managed Identity..."
az functionapp identity assign \
  --name "$FUNCTION_APP_NAME" \
  --resource-group "$RG" \
  --output none

PRINCIPAL_ID=$(az functionapp identity show --name "$FUNCTION_APP_NAME" --resource-group "$RG" --query principalId -o tsv)
STORAGE_ID=$(az storage account show --name "$STORAGE_ACCOUNT" --resource-group "$RG" --query id -o tsv)

echo ">> RBAC: 'Storage Queue Data Contributor' para la Function ($PRINCIPAL_ID) sobre $STORAGE_ACCOUNT..."
if ! ROLE_ERR=$(az role assignment create \
  --assignee "$PRINCIPAL_ID" \
  --role "Storage Queue Data Contributor" \
  --scope "$STORAGE_ID" \
  --output none 2>&1); then
  if echo "$ROLE_ERR" | grep -qi "RoleAssignmentExists"; then
    echo "   (el rol ya estaba asignado a este principal)"
  else
    echo "$ROLE_ERR" >&2
    exit 1
  fi
fi

echo ""
echo "==================== RESUMEN ===================="
echo "Function App     : $FUNCTION_APP_NAME (${PLAN_NAME:-Consumption / Y1})"
echo "Storage Account  : $STORAGE_ACCOUNT (compartida: AzureWebJobsStorage + cola de negocio)"
echo "Managed Identity : $PRINCIPAL_ID (RBAC Queue Data Contributor confirmado sobre este principal)"
echo ""
echo "App settings pendientes en el Function App:"
echo "  FraudQueueStorage__queueServiceUri=https://${STORAGE_ACCOUNT}.queue.core.windows.net"
echo "  FraudQueueStorage__credential=managedidentity"
echo "==================================================="
