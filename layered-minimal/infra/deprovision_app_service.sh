#!/usr/bin/env bash
# =============================================================
# Centinela - Script de baja: App Service Plan + App Service
# Contraparte exacta de provision_app_service.sh (Requisito 2.3 - IaC reversible)
# Región de Recursos: chilecentral | Subred: snet-app-prod
# Responsable: Santiago (PO / Infraestructura)
# =============================================================
set -euo pipefail
# ---------- VARIABLES PARAMETRIZADAS (deben ser IDÉNTICAS a provision_app_service.sh) ----------
PROJECT="centinela"
ENV="prod"
LOCATION="chilecentral"
RESOURCE_GROUP="rg-${PROJECT}-${ENV}"
APP_SERVICE_PLAN="asp-${PROJECT}-${ENV}"
APP_NAME="appcentinelaprodgrupo3"
# Datos de la VNet y Subred
VNET_NAME="vnet-centinela-prod-v3"
SUBNET_NAME="snet-app-prod"
# ---------- 1. Quitar Integración con VNet ----------
echo ">> Removiendo integración de $APP_NAME con la VNet $VNET_NAME..."
az webapp vnet-integration remove \
  --name "$APP_NAME" \
  --resource-group "$RESOURCE_GROUP" \
  --output none 2>/dev/null || echo "   (ya no existía o falló, continuando...)"
# ---------- 2. Eliminar Identidad Gestionada (System-Assigned) ----------
echo ">> Removiendo Managed Identity de $APP_NAME..."
az webapp identity remove \
  --name "$APP_NAME" \
  --resource-group "$RESOURCE_GROUP" \
  --output none 2>/dev/null || echo "   (ya no existía o falló, continuando...)"
# ---------- 3. Eliminar App Service (Web App) ----------
echo ">> Eliminando App Service: $APP_NAME..."
az webapp delete \
  --name "$APP_NAME" \
  --resource-group "$RESOURCE_GROUP" \
  --output none 2>/dev/null || echo "   (ya no existía o falló, continuando...)"
# ---------- 4. Eliminar App Service Plan ----------
echo ">> Eliminando App Service Plan: $APP_SERVICE_PLAN..."
az appservice plan delete \
  --name "$APP_SERVICE_PLAN" \
  --resource-group "$RESOURCE_GROUP" \
  --yes \
  --output none 2>/dev/null || echo "   (ya no existía o falló, continuando...)"
# ---------- 5. Quitar delegación de la subred ----------
echo ">> Removiendo delegación de la subred '$SUBNET_NAME' en $VNET_NAME..."
az network vnet subnet update \
  --resource-group "$RESOURCE_GROUP" \
  --vnet-name "$VNET_NAME" \
  --name "$SUBNET_NAME" \
  --remove delegations \
  --output none 2>/dev/null || echo "   (ya no existía o falló, continuando...)"
# ---------- 6. Resource Group ----------
# NO se borra por defecto, ya que normalmente contiene otros recursos compartidos
# (VNet, storage, etc.). Descomenta las líneas de abajo solo si quieres borrar
# TODO el grupo de recursos, incluyendo lo que no creó este script.
#
# echo ">> Eliminando Resource Group completo: $RESOURCE_GROUP..."
# az group delete --name "$RESOURCE_GROUP" --yes --no-wait
# ---------- 7. Salida Informativa ----------
echo ""
echo "==================== RESUMEN DE BAJA ===================="
echo "Resource Group : $RESOURCE_GROUP (no eliminado, ver paso 6)"
echo "App Service Plan: $APP_SERVICE_PLAN -> eliminado"
echo "App Service     : $APP_NAME -> eliminado"
echo "Subred VNet     : $SUBNET_NAME en $VNET_NAME -> delegación removida"
echo "============================================================"
echo ""
echo ">> Verifica con:"
echo "   az webapp show --name $APP_NAME --resource-group $RESOURCE_GROUP"
echo "   (debe fallar con 'ResourceNotFound' si la baja se completó)"