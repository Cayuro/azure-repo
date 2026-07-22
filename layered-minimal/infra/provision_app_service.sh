#!/usr/bin/env bash
# =============================================================
# Centinela - Script de aprovisionamiento: App Service Plan + App Service
# Región de Recursos: chilecentral | Subred: snet-app-prod
# Responsable: Santiago (PO / Infraestructura)
# Requisitos: 2.3 (IaC), 2.6 (Managed Identity), 2.9 (SKU B1 + VNet Integration)
# =============================================================
set -euo pipefail
# ---------- VARIABLES PARAMETRIZADAS ----------
PROJECT="centinela"
ENV="prod"
LOCATION="chilecentral"                              # Región de cómputo y red
RESOURCE_GROUP="rg-${PROJECT}-${ENV}"
APP_SERVICE_PLAN="asp-${PROJECT}-${ENV}"
APP_NAME="appcentinelaprodgrupo3"
SKU="B1"                                             # SKU B1 oficial con VNet Integration
RUNTIME="JAVA|21-java21"                             # Runtime Java 21 (LTS)
# Datos de la VNet y Subred
VNET_NAME="vnet-centinela-prod-v3"                   # VNet confirmada (con subredes app/data/process)
SUBNET_NAME="snet-app-prod"
# ---------- 1. Verificar Resource Group ----------
RG_LOCATION=$(az group show --name "$RESOURCE_GROUP" --query location -o tsv 2>/dev/null || echo "$LOCATION")
echo ">> Verificando Resource Group: $RESOURCE_GROUP (Ubicación RG: $RG_LOCATION)"
az group create --name "$RESOURCE_GROUP" --location "$RG_LOCATION" --output none
# ---------- 2. Delegar subred para App Service ----------
echo ">> Asegurando delegación de la subred '$SUBNET_NAME' en $VNET_NAME..."
az network vnet subnet update \
  --resource-group "$RESOURCE_GROUP" \
  --vnet-name "$VNET_NAME" \
  --name "$SUBNET_NAME" \
  --delegations "Microsoft.Web/serverFarms" \
  --output none 2>/dev/null || true
# ---------- 3. App Service Plan ----------
echo ">> Creando/Verificando App Service Plan (SKU $SKU): $APP_SERVICE_PLAN en $LOCATION..."
az appservice plan create \
  --name "$APP_SERVICE_PLAN" \
  --resource-group "$RESOURCE_GROUP" \
  --location "$LOCATION" \
  --sku "$SKU" \
  --is-linux \
  --output none
# ---------- 4. App Service (Web App) ----------
echo ">> Creando/Verificando App Service: $APP_NAME con runtime $RUNTIME en $LOCATION..."
az webapp create \
  --name "$APP_NAME" \
  --resource-group "$RESOURCE_GROUP" \
  --plan "$APP_SERVICE_PLAN" \
  --runtime "$RUNTIME" \
  --output none 2>/dev/null || true

echo ">> Forzando runtime $RUNTIME con az webapp config set..."
az webapp config set \
  --name "$APP_NAME" \
  --resource-group "$RESOURCE_GROUP" \
  --linux-fx-version "$RUNTIME" \
  --output none
# ---------- 5. Identidad Gestionada (System-Assigned) ----------
echo ">> Activando Managed Identity en $APP_NAME..."
az webapp identity assign \
  --name "$APP_NAME" \
  --resource-group "$RESOURCE_GROUP" \
  --output none
# ---------- 6. Integración con VNet ----------
echo ">> Vinculando $APP_NAME con la VNet $VNET_NAME y la subred $SUBNET_NAME..."
az webapp vnet-integration add \
  --name "$APP_NAME" \
  --resource-group "$RESOURCE_GROUP" \
  --vnet "$VNET_NAME" \
  --subnet "$SUBNET_NAME" \
  --output none

# ---------- 7. Habilitar Basic Auth en SCM (requerido para despliegue vía publish profile / GitHub Actions) ----------
# Sin este paso, cualquier despliegue por Zip Deploy o MSDeploy con publish profile falla con:
# "Publish profile is invalid for app-name and slot-name provided", aunque las credenciales sean correctas.
# Azure deshabilita Basic Auth por defecto en App Services nuevos por seguridad.
echo ">> Habilitando Basic Auth (SCM) para permitir despliegue vía publish profile..."
az resource update \
  --resource-group "$RESOURCE_GROUP" \
  --name scm \
  --namespace Microsoft.Web \
  --resource-type basicPublishingCredentialsPolicies \
  --parent "sites/${APP_NAME}" \
  --set properties.allow=true \
  --output none

# ---------- 8. Salida Informativa ----------
echo ""
echo "==================== RESUMEN DE DESPLIEGUE ===================="
echo "Resource Group : $RESOURCE_GROUP ($RG_LOCATION)"
echo "Ubicación App  : $LOCATION"
echo "App Service Plan: $APP_SERVICE_PLAN ($SKU)"
echo "App Service     : $APP_NAME"
echo "Runtime         : $RUNTIME"
echo "URL Pública     : https://${APP_NAME}.azurewebsites.net"
echo "Subred VNet     : $SUBNET_NAME en $VNET_NAME"
echo "Managed Identity: $(az webapp identity show --name "$APP_NAME" --resource-group "$RESOURCE_GROUP" --query principalId -o tsv 2>/dev/null || echo 'Pendiente')"
echo "Basic Auth SCM  : habilitado (requerido para publish profile)"
echo "================================================================"
