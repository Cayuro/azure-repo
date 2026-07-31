#!/usr/bin/env bash
# =============================================================
# SERVICIO 2/5 - Azure Communication Services (Email)
#
# Crea los tres recursos que hacen falta para poder enviar correo:
#   1. Communication Services  -> da el connection string
#   2. Email Communication Services -> contenedor del dominio de envio
#   3. Dominio "AzureManagedDomain" -> subdominio *.azurecomm.net generado y
#      verificado por Azure, sin tocar DNS propio
# y vincula (3) con (1), sin lo cual ACS no puede usar el dominio.
#
# No depende de ningun otro script: puede correrse en cualquier momento,
# aunque el orden sugerido lo pone en segundo lugar porque su salida
# (EmailSenderAddress, connection string) se necesita en 05-app-settings.sh.
#
# Ver decisiones 5 y 6 en ../01-servicios-y-arquitectura.md (por que dominio
# administrado y por que ACS no tiene variante con VNet).
# =============================================================
set -euo pipefail

ENV_FILE="${ENV_FILE:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/.env}"
if [ -f "$ENV_FILE" ]; then
  set -a; source "$ENV_FILE"; set +a
fi

RG="${RG:?Falta RG (definelo en docs/.env)}"
ACS_NAME="${ACS_NAME:?Falta ACS_NAME}"
EMAIL_SERVICE_NAME="${EMAIL_SERVICE_NAME:?Falta EMAIL_SERVICE_NAME}"
DATA_LOCATION="${DATA_LOCATION:-United States}"

echo ">> Extension 'communication' de az cli (si falta)..."
az extension add --name communication --only-show-errors 2>/dev/null || true

echo ">> Resource Group: verificando $RG..."
az group show --name "$RG" --output none

# El Resource Provider suele no estar registrado en suscripciones que nunca
# usaron ACS, y el error que da despues es confuso. Registrarlo requiere
# permisos a nivel de SUSCRIPCION (no basta Contributor sobre el RG).
echo ">> Resource Provider 'Microsoft.Communication': verificando registro..."
RP_STATE=$(az provider show --namespace Microsoft.Communication --query registrationState -o tsv 2>/dev/null || echo "NotRegistered")
if [ "$RP_STATE" != "Registered" ]; then
  echo "   No esta registrado, registrando (puede tardar 1-2 min)..."
  az provider register --namespace Microsoft.Communication
  until [ "$(az provider show --namespace Microsoft.Communication --query registrationState -o tsv)" == "Registered" ]; do
    echo "   ... esperando registro"
    sleep 5
  done
fi
echo "   OK, registrado."

# Los "create" de abajo son PUT de ARM, idempotentes por si solos: volver a
# correr el script con los mismos valores no falla. Por eso no se enmascara
# el error con '|| echo "ya existia"', que esconderia fallos reales.
echo ">> Communication Services: creando/verificando $ACS_NAME..."
az communication create \
  --name "$ACS_NAME" \
  --location "global" \
  --data-location "$DATA_LOCATION" \
  --resource-group "$RG" \
  --output none

echo ">> Email Communication Services: creando/verificando $EMAIL_SERVICE_NAME..."
az communication email create \
  --name "$EMAIL_SERVICE_NAME" \
  --location "global" \
  --data-location "$DATA_LOCATION" \
  --resource-group "$RG" \
  --output none

echo ">> Dominio administrado por Azure: creando/verificando..."
az communication email domain create \
  --domain-name "AzureManagedDomain" \
  --email-service-name "$EMAIL_SERVICE_NAME" \
  --resource-group "$RG" \
  --location "global" \
  --domain-management "AzureManaged" \
  --output none

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
# Si tu version de az cli no reconoce --linked-domains:
#   ACS_ID=$(az communication show --name "$ACS_NAME" -g "$RG" --query id -o tsv)
#   az resource update --ids "$ACS_ID" --set "properties.linkedDomains=[\"$DOMAIN_ID\"]"
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
echo "Email Service          : $EMAIL_SERVICE_NAME"
echo ""
echo "Para local.settings.json / App Settings:"
echo "  EmailSenderAddress=DoNotReply@${FROM_SENDER_DOMAIN}"
echo ""
echo "CommunicationServicesConnectionString es SECRETO (se imprime abajo una"
echo "sola vez). No lo pegues en chats, tickets ni lo subas al repo."
echo "Para volver a consultarlo:"
echo "  az communication list-key --name $ACS_NAME -g $RG --query primaryConnectionString -o tsv"
echo ""
echo "Siguiente paso: bash docs/scripts/03-app-service-plan.sh"
echo "================================================="
echo "$CONNECTION_STRING"
