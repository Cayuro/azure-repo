#!/usr/bin/env bash
# =============================================================
# Storage Queue (variante SIN VNet) - acceso publico + RBAC
#
# Crea/verifica la Storage Account y la cola de eventos de fraude (+ su
# cola de poison messages). Queda con acceso publico normal: el productor
# (App Service) y el consumidor (Function) le hablan por HTTPS publico,
# autenticados por Managed Identity + RBAC (sin claves ni connection
# strings).
#
# En esta variante, esta MISMA cuenta es tambien la que usa la Function
# como AzureWebJobsStorage (correr este script ANTES que
# provision_function_app.sh, que la reutiliza en vez de crear una propia).
#
# Generico: solo cambia las variables de abajo (nombre, ubicacion, etc).
# =============================================================
set -euo pipefail

RG="${RG:?export RG=<resource-group>}"
LOCATION="${LOCATION:?export LOCATION=<region>, ej. eastus}"
STORAGE_ACCOUNT="${STORAGE_ACCOUNT:?export STORAGE_ACCOUNT=<nombre-storage-account>}"
QUEUE_NAME="${QUEUE_NAME:-cola-casos-fraude}"
QUEUE_POISON_NAME="${QUEUE_POISON_NAME:-${QUEUE_NAME}-poison}"
SKU="${SKU:-Standard_LRS}"

# Principals opcionales a los que otorgar acceso RBAC sobre la cola.
# Dejar vacio y asignar despues si aun no tienes los principalId.
# CONSUMER_PRINCIPAL_ID normalmente no hace falta pasarlo aqui: como este
# script corre ANTES que la Function exista, provision_function_app.sh se
# encarga de asignarse su propio RBAC al final. Dejalo solo si ya conoces
# el principalId de antemano o quieres agregar un consumidor adicional.
PRODUCER_PRINCIPAL_ID="${PRODUCER_PRINCIPAL_ID:-}"   # Managed Identity del App Service que publica
CONSUMER_PRINCIPAL_ID="${CONSUMER_PRINCIPAL_ID:-}"   # Managed Identity de la Function (opcional, ver arriba)

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
  --output none 2>/dev/null || echo "   (ya existia)"

echo ">> Cola principal: creando/verificando $QUEUE_NAME..."
az storage queue create \
  --account-name "$STORAGE_ACCOUNT" \
  --name "$QUEUE_NAME" \
  --auth-mode login \
  --output none

echo ">> Cola de poison messages: creando/verificando $QUEUE_POISON_NAME..."
az storage queue create \
  --account-name "$STORAGE_ACCOUNT" \
  --name "$QUEUE_POISON_NAME" \
  --auth-mode login \
  --output none

STORAGE_ID=$(az storage account show --name "$STORAGE_ACCOUNT" --resource-group "$RG" --query id -o tsv)

if [ -n "$PRODUCER_PRINCIPAL_ID" ]; then
  echo ">> RBAC: 'Storage Queue Data Contributor' para el productor..."
  az role assignment create \
    --assignee "$PRODUCER_PRINCIPAL_ID" \
    --role "Storage Queue Data Contributor" \
    --scope "$STORAGE_ID" \
    --output none 2>/dev/null || true
else
  echo "!! PRODUCER_PRINCIPAL_ID vacio, se omite el RBAC del productor."
fi

if [ -n "$CONSUMER_PRINCIPAL_ID" ]; then
  echo ">> RBAC: 'Storage Queue Data Contributor' para el consumidor (Function)..."
  az role assignment create \
    --assignee "$CONSUMER_PRINCIPAL_ID" \
    --role "Storage Queue Data Contributor" \
    --scope "$STORAGE_ID" \
    --output none 2>/dev/null || true
else
  echo "!! CONSUMER_PRINCIPAL_ID vacio, se omite el RBAC del consumidor."
fi

echo ""
echo "==================== RESUMEN ===================="
echo "Storage Account : $STORAGE_ACCOUNT ($LOCATION, acceso publico)"
echo "Cola principal  : $QUEUE_NAME"
echo "Cola poison     : $QUEUE_POISON_NAME"
echo ""
echo "App settings para la Function (FraudQueueStorage), identity-based:"
echo "  FraudQueueStorage__queueServiceUri=https://${STORAGE_ACCOUNT}.queue.core.windows.net"
echo "  FraudQueueStorage__credential=managedidentity"
echo "  FraudQueueName=${QUEUE_NAME}"
echo "==================================================="
