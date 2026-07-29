#!/usr/bin/env bash
# =============================================================
# Centinela - Script de aprovisionamiento: Azure AI Document Intelligence
# Región de Recursos: chilecentral
# Requisitos: 2.3 (IaC), 2.6 (Managed Identity / sin claves), 2.9 (RBAC minimo)
# =============================================================
set -euo pipefail
# ---------- VARIABLES PARAMETRIZADAS ----------
PROJECT="${PROJECT:-centinela}"
ENV="${ENV:-prod}"
LOCATION="${LOCATION:-chilecentral}"                              # Misma region que el resto de recursos
RESOURCE_GROUP="${RESOURCE_GROUP:-rg-${PROJECT}-${ENV}}"
APP_NAME="${APP_NAME:-appcentinelaprodgrupo3}"                    # App Service consumidor (managed identity)
DOCUMENT_INTELLIGENCE_NAME="${DOCUMENT_INTELLIGENCE_NAME:-di-centinela-${ENV}}"
SKU="${SKU:-S0}"
# ---------- 1. Verificar Resource Group ----------
RG_LOCATION=$(az group show --name "$RESOURCE_GROUP" --query location -o tsv 2>/dev/null || echo "$LOCATION")
echo ">> Verificando Resource Group: $RESOURCE_GROUP (Ubicación RG: $RG_LOCATION)"
az group create --name "$RESOURCE_GROUP" --location "$RG_LOCATION" --output none
# ---------- 2. Recurso de Document Intelligence (Cognitive Services, kind FormRecognizer) ----------
echo ">> Creando/Verificando recurso de Document Intelligence: $DOCUMENT_INTELLIGENCE_NAME ($SKU) en $LOCATION..."
az cognitiveservices account create \
  --name "$DOCUMENT_INTELLIGENCE_NAME" \
  --resource-group "$RESOURCE_GROUP" \
  --location "$LOCATION" \
  --kind FormRecognizer \
  --sku "$SKU" \
  --custom-domain "$DOCUMENT_INTELLIGENCE_NAME" \
  --output none
# ---------- 3. RBAC: Managed Identity del App Service sobre el recurso ----------
PRINCIPAL_ID=$(az webapp identity show --name "$APP_NAME" --resource-group "$RESOURCE_GROUP" --query principalId -o tsv 2>/dev/null || echo "")
DOCUMENT_INTELLIGENCE_ID=$(az cognitiveservices account show --name "$DOCUMENT_INTELLIGENCE_NAME" --resource-group "$RESOURCE_GROUP" --query id -o tsv)

if [ -n "$PRINCIPAL_ID" ]; then
  echo ">> Asignando 'Cognitive Services User' a la Managed Identity de $APP_NAME..."
  az role assignment create \
    --assignee "$PRINCIPAL_ID" \
    --role "Cognitive Services User" \
    --scope "$DOCUMENT_INTELLIGENCE_ID" \
    --output none 2>/dev/null || true
else
  echo "!! No se encontro Managed Identity en $APP_NAME (¿ya se corrio provision_app_service.sh?). Se omite la asignacion de roles."
fi
# ---------- 4. Salida Informativa ----------
ENDPOINT=$(az cognitiveservices account show --name "$DOCUMENT_INTELLIGENCE_NAME" --resource-group "$RESOURCE_GROUP" --query properties.endpoint -o tsv)
echo ""
echo "==================== RESUMEN DE DESPLIEGUE ===================="
echo "Resource Group        : $RESOURCE_GROUP ($RG_LOCATION)"
echo "Document Intelligence  : $DOCUMENT_INTELLIGENCE_NAME ($SKU, $LOCATION)"
echo "Endpoint               : $ENDPOINT"
echo "Managed Identity       : ${PRINCIPAL_ID:-Pendiente (App Service sin identidad)}"
echo ""
echo "Actualiza azure.documentintelligence.endpoint en application.properties con el Endpoint de arriba."
echo "================================================================"
