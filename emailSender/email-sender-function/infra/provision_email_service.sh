#!/usr/bin/env bash
# =============================================================
# Azure Communication Services - Email (dominio administrado por Azure)
#
# Un solo script, sin variante de VNet: Azure Communication Services /
# Email no soporta Private Endpoint hoy, asi que la llamada siempre sale
# por el endpoint publico de ACS, tengas o no VNet Integration en tus
# otros recursos. "Meterlo dentro de la VNet" no es una opcion disponible
# actualmente, asi que no tiene sentido duplicar este script.
#
# Usa el dominio por defecto de Azure (AzureManagedDomain): un subdominio
# tipo xxxxxxxx.azurecomm.net ya verificado, sin tocar tu DNS. El remitente
# queda fijo como DoNotReply@<ese-subdominio>.azurecomm.net.
#
# Generico: solo cambia las variables de abajo.
# =============================================================
set -euo pipefail

RG="${RG:?export RG=<resource-group>}"
DATA_LOCATION="${DATA_LOCATION:-United States}"   # United States | Europe | UK | Brazil | Asia Pacific | Australia | Canada | ...
ACS_NAME="${ACS_NAME:?export ACS_NAME=<nombre-communication-services>}"
EMAIL_SERVICE_NAME="${EMAIL_SERVICE_NAME:?export EMAIL_SERVICE_NAME=<nombre-email-service>}"

echo ">> Extension 'communication' de az cli (si falta)..."
az extension add --name communication --only-show-errors 2>/dev/null || true

echo ">> Resource Group: verificando $RG..."
az group show --name "$RG" --output none

echo ">> Communication Services: creando/verificando $ACS_NAME..."
az communication create \
  --name "$ACS_NAME" \
  --location "global" \
  --data-location "$DATA_LOCATION" \
  --resource-group "$RG" \
  --output none 2>/dev/null || echo "   (ya existia)"

echo ">> Email Communication Services: creando/verificando $EMAIL_SERVICE_NAME..."
az communication email create \
  --name "$EMAIL_SERVICE_NAME" \
  --location "global" \
  --data-location "$DATA_LOCATION" \
  --resource-group "$RG" \
  --output none 2>/dev/null || echo "   (ya existia)"

echo ">> Dominio administrado por Azure: creando/verificando..."
az communication email domain create \
  --domain-name "AzureManagedDomain" \
  --email-service-name "$EMAIL_SERVICE_NAME" \
  --resource-group "$RG" \
  --location "global" \
  --domain-management "AzureManaged" \
  --output none 2>/dev/null || echo "   (ya existia)"

DOMAIN_ID=$(az communication email domain show \
  --domain-name "AzureManagedDomain" \
  --email-service-name "$EMAIL_SERVICE_NAME" \
  --resource-group "$RG" \
  --query "id" -o tsv)

FROM_SENDER_DOMAIN=$(az communication email domain show \
  --domain-name "AzureManagedDomain" \
  --email-service-name "$EMAIL_SERVICE_NAME" \
  --resource-group "$RG" \
  --query "fromSenderDomain" -o tsv)

echo ">> Vinculando el dominio al recurso Communication Services..."
az communication update \
  --name "$ACS_NAME" \
  --resource-group "$RG" \
  --linked-domains "$DOMAIN_ID" \
  --output none

CONNECTION_STRING=$(az communication list-key \
  --name "$ACS_NAME" \
  --resource-group "$RG" \
  --query "primaryConnectionString" -o tsv)

echo ""
echo "==================== RESUMEN ===================="
echo "Communication Services : $ACS_NAME"
echo "Email Service           : $EMAIL_SERVICE_NAME"
echo "Dominio                 : DoNotReply@${FROM_SENDER_DOMAIN}"
echo ""
echo "App settings para la Function:"
echo "  EmailSenderAddress=DoNotReply@${FROM_SENDER_DOMAIN}"
echo ""
echo "CommunicationServicesConnectionString es SECRETO (se imprime abajo una"
echo "sola vez) - no lo dejes en logs ni consola compartida. Para volver a"
echo "consultarlo despues:"
echo "  az communication list-key --name $ACS_NAME -g $RG --query primaryConnectionString -o tsv"
echo "==================================================="
echo "$CONNECTION_STRING"
