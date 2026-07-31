#!/usr/bin/env bash
# =============================================================
# SERVICIO 3/5 - App Service Plan dedicado para la Function
#
# Plan Linux propio donde corre la Function App. Existe por dos razones
# (ver decisiones 1 y 4 en ../01-servicios-y-arquitectura.md):
#
#   1. chilecentral NO soporta el plan Consumption dinamico para Linux
#      ("Linux dynamic workers are not available in resource group"), asi que
#      la opcion serverless barata no esta disponible en esta region.
#   2. Reutilizar el plan del App Service de scoring parecia gratis, pero un
#      B1 tiene ~1.75 GB de RAM y dos JVMs compitiendo ahi provocan reinicios
#      erraticos del contenedor. Un plan propio aisla el problema.
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
PLAN_NAME="${PLAN_NAME:?Falta PLAN_NAME}"
PLAN_SKU="${PLAN_SKU:-B1}"

echo ">> Resource Group: verificando $RG..."
az group show --name "$RG" --output none

echo ">> App Service Plan: creando/verificando $PLAN_NAME ($PLAN_SKU, Linux)..."
az appservice plan create \
  --name "$PLAN_NAME" \
  --resource-group "$RG" \
  --location "$LOCATION" \
  --sku "$PLAN_SKU" \
  --is-linux \
  --output none

echo ""
echo "==================== RESUMEN ===================="
echo "App Service Plan : $PLAN_NAME ($PLAN_SKU, Linux, $LOCATION)"
echo ""
echo "Este plan tiene costo fijo mensual (no es pago por ejecucion)."
echo "Para apagarlo sin borrar nada, se puede escalar a F1 (gratis) o"
echo "detener la Function App."
echo ""
echo "Siguiente paso: bash docs/scripts/04-function-app.sh"
echo "================================================="
