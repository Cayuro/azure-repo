#!/usr/bin/env bash
# =============================================================
# Function App (variante CON VNet) - plan Flex Consumption
#
# Crea la storage account propia de runtime, el Function App en Flex
# Consumption (pago por ejecucion, soporta VNet Integration a diferencia
# del Consumption clasico), y la integra a una subred dedicada de la VNet
# con Route All activado (todo el trafico saliente pasa por la VNet).
#
# ADVERTENCIA - verificar antes de correr (sintaxis de Flex Consumption
# relativamente nueva, ha cambiado entre versiones de az cli):
#   - Flag `--flexconsumption-location`:
#       az functionapp create --help | grep -i flexconsumption
#   - Delegacion de subred requerida (aqui se asume "Microsoft.App/environments";
#     confirmar contra la doc vigente de "Flex Consumption plan networking").
#   - Property path exacto de vnetRouteAllEnabled (aqui se usa
#     siteConfig.vnetRouteAllEnabled).
#
# Generico: solo cambia las variables de abajo.
# =============================================================
set -euo pipefail

RG="${RG:?export RG=<resource-group>}"
LOCATION="${LOCATION:?export LOCATION=<region que soporte Flex Consumption>}"
VNET_NAME="${VNET_NAME:?export VNET_NAME=<vnet-existente>}"
FUNCTION_SUBNET_NAME="${FUNCTION_SUBNET_NAME:-snet-func-email-sender}"
FUNCTION_SUBNET_PREFIX="${FUNCTION_SUBNET_PREFIX:-10.0.10.0/26}"
FUNCTION_APP_NAME="${FUNCTION_APP_NAME:?export FUNCTION_APP_NAME=<nombre-function-app>}"

DEFAULT_STORAGE=$(echo "${FUNCTION_APP_NAME}fnsa" | tr -cd 'a-zA-Z0-9' | tr 'A-Z' 'a-z' | cut -c1-24)
FUNCTION_STORAGE_ACCOUNT="${FUNCTION_STORAGE_ACCOUNT:-$DEFAULT_STORAGE}"

echo ">> Resource Group: verificando $RG..."
az group show --name "$RG" --output none

echo ">> Subred de integracion: creando/verificando $FUNCTION_SUBNET_NAME..."
az network vnet subnet create \
  --resource-group "$RG" \
  --vnet-name "$VNET_NAME" \
  --name "$FUNCTION_SUBNET_NAME" \
  --address-prefixes "$FUNCTION_SUBNET_PREFIX" \
  --delegations "Microsoft.App/environments" \
  --output none 2>/dev/null || echo "   (ya existia o hay que ajustar la delegacion a mano)"

echo ">> Storage Account de runtime: creando/verificando $FUNCTION_STORAGE_ACCOUNT..."
az storage account create \
  --name "$FUNCTION_STORAGE_ACCOUNT" \
  --resource-group "$RG" \
  --location "$LOCATION" \
  --sku Standard_LRS \
  --kind StorageV2 \
  --min-tls-version TLS1_2 \
  --https-only true \
  --output none 2>/dev/null || echo "   (ya existia)"

echo ">> Function App (Flex Consumption): creando/verificando $FUNCTION_APP_NAME..."
az functionapp create \
  --resource-group "$RG" \
  --name "$FUNCTION_APP_NAME" \
  --storage-account "$FUNCTION_STORAGE_ACCOUNT" \
  --flexconsumption-location "$LOCATION" \
  --runtime java \
  --runtime-version 21 \
  --os-type Linux \
  --output none 2>/dev/null || echo "   (ya existia)"

echo ">> Activando Managed Identity..."
az functionapp identity assign \
  --name "$FUNCTION_APP_NAME" \
  --resource-group "$RG" \
  --output none

echo ">> VNet Integration: $VNET_NAME / $FUNCTION_SUBNET_NAME..."
az functionapp vnet-integration add \
  --name "$FUNCTION_APP_NAME" \
  --resource-group "$RG" \
  --vnet "$VNET_NAME" \
  --subnet "$FUNCTION_SUBNET_NAME" \
  --output none

echo ">> Activando Route All..."
az functionapp update \
  --name "$FUNCTION_APP_NAME" \
  --resource-group "$RG" \
  --set 'siteConfig.vnetRouteAllEnabled=true' \
  --output none
echo "   Verificar: az functionapp show --name $FUNCTION_APP_NAME -g $RG --query siteConfig.vnetRouteAllEnabled"

PRINCIPAL_ID=$(az functionapp identity show --name "$FUNCTION_APP_NAME" --resource-group "$RG" --query principalId -o tsv)

echo ""
echo "==================== RESUMEN ===================="
echo "Function App     : $FUNCTION_APP_NAME (Flex Consumption)"
echo "Storage runtime  : $FUNCTION_STORAGE_ACCOUNT (AzureWebJobsStorage, publica, sin datos de negocio)"
echo "VNet Integration : $VNET_NAME / $FUNCTION_SUBNET_NAME (Route All ON, verificar)"
echo "Managed Identity : $PRINCIPAL_ID"
echo ""
echo "Usa este valor como CONSUMER_PRINCIPAL_ID al correr la variante"
echo "con-vnet de provision_storage_queue.sh, para darle RBAC sobre la cola."
echo "==================================================="
