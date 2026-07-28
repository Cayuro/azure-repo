#!/usr/bin/env bash
set -euo pipefail

PROJECT="centinela"
ENV="prod"
RESOURCE_GROUP="rg-${PROJECT}-${ENV}"
APP_NAME="appcentinelaprodgrupo3"
APP_SERVICE_PLAN="asp-${PROJECT}-${ENV}"

echo ">> Verificando existencia de $APP_NAME en $RESOURCE_GROUP..."
if ! az webapp show --name "$APP_NAME" --resource-group "$RESOURCE_GROUP" --output none 2>/dev/null; then
  echo "!! El App Service '$APP_NAME' no existe. Nada que apagar."
  exit 0
fi

echo ">> Deteniendo App Service: $APP_NAME..."
az webapp stop \
  --name "$APP_NAME" \
  --resource-group "$RESOURCE_GROUP" \
  --output none

STATE=$(az webapp show --name "$APP_NAME" --resource-group "$RESOURCE_GROUP" --query state -o tsv)

echo ""
echo "==================== RESUMEN DE APAGADO ===================="
echo "Resource Group : $RESOURCE_GROUP"
echo "App Service     : $APP_NAME"
echo "Estado actual   : $STATE"
echo "Fecha/hora      : $(date '+%Y-%m-%d %H:%M:%S')"
echo "=============================================================="
