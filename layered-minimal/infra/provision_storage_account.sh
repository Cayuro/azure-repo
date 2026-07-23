#!/usr/bin/env bash
# =============================================================
# Centinela - Script de aprovisionamiento: Storage Account (Blob + Queues)
# Región de Recursos: chilecentral
# Responsable: Santiago (PO / Infraestructura)
# Requisitos: 2.3 (IaC), 2.6 (Managed Identity / sin claves), 2.9 (RBAC minimo)
# =============================================================
set -euo pipefail
# ---------- VARIABLES PARAMETRIZADAS ----------
PROJECT="centinela"
ENV="prod"
LOCATION="chilecentral"                              # Misma region que el App Service
RESOURCE_GROUP="rg-${PROJECT}-${ENV}"
APP_NAME="appcentinelaprodgrupo3"                    # App Service consumidor (managed identity)
STORAGE_ACCOUNT="sttransaccionesfase1"
SKU="Standard_LRS"
CONTAINER_NAME="evidencias-financieras-privado"
QUEUE_NAME="cola-transacciones-ingesta"
QUEUE_POISON_NAME="cola-transacciones-ingesta-poison"
# ---------- 1. Verificar Resource Group ----------
RG_LOCATION=$(az group show --name "$RESOURCE_GROUP" --query location -o tsv 2>/dev/null || echo "$LOCATION")
echo ">> Verificando Resource Group: $RESOURCE_GROUP (Ubicación RG: $RG_LOCATION)"
az group create --name "$RESOURCE_GROUP" --location "$RG_LOCATION" --output none
# ---------- 2. Storage Account ----------
echo ">> Creando/Verificando Storage Account: $STORAGE_ACCOUNT ($SKU) en $LOCATION..."
az storage account create \
  --name "$STORAGE_ACCOUNT" \
  --resource-group "$RESOURCE_GROUP" \
  --location "$LOCATION" \
  --sku "$SKU" \
  --kind StorageV2 \
  --min-tls-version TLS1_2 \
  --https-only true \
  --allow-blob-public-access false \
  --allow-shared-key-access false \
  --output none
# ---------- 3. Contenedor de Blobs (evidencias) ----------
echo ">> Creando/Verificando contenedor privado: $CONTAINER_NAME..."
az storage container create \
  --account-name "$STORAGE_ACCOUNT" \
  --name "$CONTAINER_NAME" \
  --public-access off \
  --auth-mode login \
  --output none
# ---------- 4. Colas de ingesta (principal + poison) ----------
echo ">> Creando/Verificando cola principal: $QUEUE_NAME..."
az storage queue create \
  --account-name "$STORAGE_ACCOUNT" \
  --name "$QUEUE_NAME" \
  --auth-mode login \
  --output none

echo ">> Creando/Verificando cola de poison messages: $QUEUE_POISON_NAME..."
az storage queue create \
  --account-name "$STORAGE_ACCOUNT" \
  --name "$QUEUE_POISON_NAME" \
  --auth-mode login \
  --output none
# ---------- 5. RBAC: Managed Identity del App Service sobre el Storage Account ----------
PRINCIPAL_ID=$(az webapp identity show --name "$APP_NAME" --resource-group "$RESOURCE_GROUP" --query principalId -o tsv 2>/dev/null || echo "")
STORAGE_ID=$(az storage account show --name "$STORAGE_ACCOUNT" --resource-group "$RESOURCE_GROUP" --query id -o tsv)

if [ -n "$PRINCIPAL_ID" ]; then
  echo ">> Asignando 'Storage Blob Data Contributor' a la Managed Identity de $APP_NAME..."
  az role assignment create \
    --assignee "$PRINCIPAL_ID" \
    --role "Storage Blob Data Contributor" \
    --scope "$STORAGE_ID" \
    --output none 2>/dev/null || true

  echo ">> Asignando 'Storage Queue Data Contributor' a la Managed Identity de $APP_NAME..."
  az role assignment create \
    --assignee "$PRINCIPAL_ID" \
    --role "Storage Queue Data Contributor" \
    --scope "$STORAGE_ID" \
    --output none 2>/dev/null || true
else
  echo "!! No se encontro Managed Identity en $APP_NAME (¿ya se corrio provision_app_service.sh?). Se omite la asignacion de roles."
fi
# ---------- 6. Salida Informativa ----------
echo ""
echo "==================== RESUMEN DE DESPLIEGUE ===================="
echo "Resource Group   : $RESOURCE_GROUP ($RG_LOCATION)"
echo "Storage Account   : $STORAGE_ACCOUNT ($SKU, $LOCATION)"
echo "Contenedor Blob   : $CONTAINER_NAME (privado, sin claves)"
echo "Cola principal    : $QUEUE_NAME"
echo "Cola poison       : $QUEUE_POISON_NAME"
echo "Managed Identity  : ${PRINCIPAL_ID:-Pendiente (App Service sin identidad)}"
echo "================================================================"
