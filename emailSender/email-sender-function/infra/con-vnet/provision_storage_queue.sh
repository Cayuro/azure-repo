#!/usr/bin/env bash
# =============================================================
# Storage Queue (variante CON VNet) - acceso privado via Private Endpoint
#
# Crea/verifica la Storage Account + colas, bloquea su acceso publico, y la
# expone solo dentro de la VNet via Private Endpoint (subrecurso "queue")
# + Private DNS Zone. Cualquier cliente (App Service, Function) que
# necesite hablarle DEBE tener VNet Integration regional hacia esta misma
# VNet, o no podra resolverla ni alcanzarla.
#
# Requiere que la VNet ya exista. Crea (o reutiliza) una subred dedicada a
# Private Endpoints.
#
# Generico: solo cambia las variables de abajo.
# =============================================================
set -euo pipefail

RG="${RG:?export RG=<resource-group>}"
LOCATION="${LOCATION:?export LOCATION=<region>}"
VNET_NAME="${VNET_NAME:?export VNET_NAME=<vnet-existente>}"
PE_SUBNET_NAME="${PE_SUBNET_NAME:-snet-private-endpoints}"
PE_SUBNET_PREFIX="${PE_SUBNET_PREFIX:-10.0.11.0/26}"

STORAGE_ACCOUNT="${STORAGE_ACCOUNT:?export STORAGE_ACCOUNT=<nombre-storage-account>}"
QUEUE_NAME="${QUEUE_NAME:-cola-casos-fraude}"
QUEUE_POISON_NAME="${QUEUE_POISON_NAME:-${QUEUE_NAME}-poison}"
SKU="${SKU:-Standard_LRS}"

PRODUCER_PRINCIPAL_ID="${PRODUCER_PRINCIPAL_ID:-}"   # Managed Identity del App Service que publica
CONSUMER_PRINCIPAL_ID="${CONSUMER_PRINCIPAL_ID:-}"   # Managed Identity de la Function que consume

echo ">> Resource Group: verificando $RG..."
az group show --name "$RG" --output none

echo ">> Storage Account: creando/verificando $STORAGE_ACCOUNT..."
az storage account create \
  --name "$STORAGE_ACCOUNT" \
  --resource-group "$RG" \
  --location "$LOCATION" \
  --sku "$SKU" \
  --kind StorageV2 \
  --min-tls-version TLS1_2 \
  --https-only true \
  --allow-blob-public-access false \
  --output none

echo ">> Cola principal: creando/verificando $QUEUE_NAME..."
az storage queue create --account-name "$STORAGE_ACCOUNT" --name "$QUEUE_NAME" --auth-mode login --output none

echo ">> Cola de poison messages: creando/verificando $QUEUE_POISON_NAME..."
az storage queue create --account-name "$STORAGE_ACCOUNT" --name "$QUEUE_POISON_NAME" --auth-mode login --output none

echo ">> Subred de Private Endpoints: creando/verificando $PE_SUBNET_NAME..."
az network vnet subnet create \
  --resource-group "$RG" \
  --vnet-name "$VNET_NAME" \
  --name "$PE_SUBNET_NAME" \
  --address-prefixes "$PE_SUBNET_PREFIX" \
  --private-endpoint-network-policies Disabled \
  --output none

echo ">> Restringiendo acceso publico de $STORAGE_ACCOUNT..."
az storage account update \
  --name "$STORAGE_ACCOUNT" \
  --resource-group "$RG" \
  --default-action Deny \
  --bypass AzureServices \
  --output none

STORAGE_ID=$(az storage account show --name "$STORAGE_ACCOUNT" --resource-group "$RG" --query id -o tsv)
PE_NAME="pe-${STORAGE_ACCOUNT}-queue"

echo ">> Private Endpoint: creando/verificando $PE_NAME..."
az network private-endpoint create \
  --name "$PE_NAME" \
  --resource-group "$RG" \
  --vnet-name "$VNET_NAME" \
  --subnet "$PE_SUBNET_NAME" \
  --private-connection-resource-id "$STORAGE_ID" \
  --group-id queue \
  --connection-name "cn-${STORAGE_ACCOUNT}-queue" \
  --output none

echo ">> Private DNS Zone: creando/verificando..."
az network private-dns zone create \
  --resource-group "$RG" \
  --name "privatelink.queue.core.windows.net" \
  --output none

az network private-dns link vnet create \
  --resource-group "$RG" \
  --zone-name "privatelink.queue.core.windows.net" \
  --name "link-${VNET_NAME}-queue" \
  --virtual-network "$VNET_NAME" \
  --registration-enabled false \
  --output none

az network private-endpoint dns-zone-group create \
  --resource-group "$RG" \
  --endpoint-name "$PE_NAME" \
  --name "zg-queue" \
  --private-dns-zone "privatelink.queue.core.windows.net" \
  --zone-name "queue" \
  --output none

if [ -n "$PRODUCER_PRINCIPAL_ID" ]; then
  echo ">> RBAC: 'Storage Queue Data Contributor' para el productor..."
  az role assignment create \
    --assignee "$PRODUCER_PRINCIPAL_ID" \
    --role "Storage Queue Data Contributor" \
    --scope "$STORAGE_ID" \
    --output none 2>/dev/null || true
else
  echo "!! PRODUCER_PRINCIPAL_ID vacio, se omite. IMPORTANTE: el productor tambien"
  echo "   debe estar VNet-integrado a $VNET_NAME para poder alcanzar la cuenta"
  echo "   (ahora es privada), sin importar el RBAC."
fi

if [ -n "$CONSUMER_PRINCIPAL_ID" ]; then
  echo ">> RBAC: 'Storage Queue Data Contributor' para el consumidor (Function)..."
  az role assignment create \
    --assignee "$CONSUMER_PRINCIPAL_ID" \
    --role "Storage Queue Data Contributor" \
    --scope "$STORAGE_ID" \
    --output none 2>/dev/null || true
else
  echo "!! CONSUMER_PRINCIPAL_ID vacio, se omite."
fi

echo ""
echo "==================== RESUMEN ===================="
echo "Storage Account  : $STORAGE_ACCOUNT (acceso publico DENY)"
echo "Cola principal   : $QUEUE_NAME"
echo "Cola poison      : $QUEUE_POISON_NAME"
echo "Private Endpoint : $PE_NAME (subresource: queue)"
echo ""
echo "IMPORTANTE: cualquier cliente que necesite hablarle a esta cuenta"
echo "(App Service, Function) debe tener VNet Integration regional hacia"
echo "$VNET_NAME. Si corres esta variante, la Function tambien debe usar"
echo "la variante con-vnet de provision_function_app.sh."
echo ""
echo "App settings para la Function (FraudQueueStorage), identity-based:"
echo "  FraudQueueStorage__queueServiceUri=https://${STORAGE_ACCOUNT}.queue.core.windows.net"
echo "  FraudQueueStorage__credential=managedidentity"
echo "  FraudQueueName=${QUEUE_NAME}"
echo "==================================================="
