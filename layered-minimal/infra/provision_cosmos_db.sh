#!/usr/bin/env bash
# =============================================================
# Centinela - Aprovisionamiento de Azure Cosmos DB
#
# Crea la base de datos NoSQL donde se guardan las transacciones y los
# scores de riesgo (CosmosTransactionRepository / CosmosTransactionScoreRepository).
#
# Por que Cosmos DB para esto: las transacciones tienen escritura constante
# y de alto volumen; particionar por accountId hace que "dame el historial
# de esta cuenta" (usado por el motor de scoring) sea muy rapido, porque
# todos los documentos de esa cuenta quedan en la misma particion.
#
# Nombres de base de datos/containers/partition keys DEBEN coincidir con
# las anotaciones @Container/@PartitionKey de TransactionEntity y
# TransactionScoreEntity -- no cambiar sin actualizar tambien el codigo.
#
# Requisitos: Azure CLI con sesion activa (az login). El App Service y el
# Key Vault son opcionales: si no existen todavia, el script lo indica y
# sigue (el endpoint igual queda impreso al final para configurarlo a mano).
#
# Uso: bash provision_cosmos_db.sh
# =============================================================
set -euo pipefail

# ---------- VARIABLES PARAMETRIZADAS ----------
PROJECT="${PROJECT:-centinela}"
ENV="${ENV:-prod}"
LOCATION="${LOCATION:-chilecentral}"
RESOURCE_GROUP="${RESOURCE_GROUP:-rg-${PROJECT}-${ENV}}"
COSMOS_ACCOUNT="${COSMOS_ACCOUNT:-cosmos-centinela-prod}"
COSMOS_DB="${COSMOS_DB:-centinela-db}"
APP_NAME="${APP_NAME:-appcentinelaprodgrupo3}"
KEY_VAULT="${KEY_VAULT:-kv-centinela-prod}"

# ---------- 1. Verificar Resource Group ----------
RG_LOCATION=$(az group show --name "$RESOURCE_GROUP" --query location -o tsv 2>/dev/null || echo "$LOCATION")
echo ">> Verificando Resource Group: $RESOURCE_GROUP (Ubicacion RG: $RG_LOCATION)"
az group create --name "$RESOURCE_GROUP" --location "$RG_LOCATION" --output none

# ---------- 2. Cuenta de Cosmos DB (API SQL/NoSQL, consistencia Session) ----------
echo ">> Creando/Verificando cuenta de Cosmos DB: $COSMOS_ACCOUNT..."
az cosmosdb create \
  --name "$COSMOS_ACCOUNT" \
  --resource-group "$RESOURCE_GROUP" \
  --locations regionName="$LOCATION" failoverPriority=0 \
  --default-consistency-level "Session" \
  --output none

# ---------- 3. Base de datos ----------
echo ">> Creando/Verificando base de datos: $COSMOS_DB..."
az cosmosdb sql database create \
  --account-name "$COSMOS_ACCOUNT" \
  --resource-group "$RESOURCE_GROUP" \
  --name "$COSMOS_DB" \
  --output none

# ---------- 4. Contenedor de transacciones (particion por /accountId) ----------
echo ">> Creando/Verificando contenedor 'transacciones'..."
az cosmosdb sql container create \
  --account-name "$COSMOS_ACCOUNT" \
  --resource-group "$RESOURCE_GROUP" \
  --database-name "$COSMOS_DB" \
  --name "transacciones" \
  --partition-key-path "/accountId" \
  --throughput 400 \
  --output none

# ---------- 5. Contenedor de scores (particion por /transactionId) ----------
echo ">> Creando/Verificando contenedor 'scores'..."
az cosmosdb sql container create \
  --account-name "$COSMOS_ACCOUNT" \
  --resource-group "$RESOURCE_GROUP" \
  --database-name "$COSMOS_DB" \
  --name "scores" \
  --partition-key-path "/transactionId" \
  --throughput 400 \
  --output none

COSMOS_ENDPOINT=$(az cosmosdb show --name "$COSMOS_ACCOUNT" --resource-group "$RESOURCE_GROUP" --query documentEndpoint -o tsv)
COSMOS_ID=$(az cosmosdb show --name "$COSMOS_ACCOUNT" --resource-group "$RESOURCE_GROUP" --query id -o tsv)

# ---------- 6. Guardar el endpoint en Key Vault (si ya existe) ----------
if az keyvault show --name "$KEY_VAULT" --output none 2>/dev/null; then
  echo ">> Guardando endpoint en Key Vault ($KEY_VAULT)..."
  az keyvault secret set --vault-name "$KEY_VAULT" --name "cosmos-endpoint" --value "$COSMOS_ENDPOINT" --output none
else
  echo "!! Key Vault $KEY_VAULT no encontrado; se omite guardar el secreto. El endpoint queda impreso abajo."
fi

# ---------- 7. RBAC: 'Cosmos DB Built-in Data Contributor' (rol de datos, sin claves) ----------
# Con Managed Identity/Azure AD, el cliente se identifica ante Cosmos con un token,
# nunca con contraseñas/claves de cuenta (mismo patron "sin claves" del resto del proyecto).
asignar_rol_cosmos() {
  local principal_id="$1"
  local descripcion="$2"
  if [ -z "$principal_id" ]; then
    echo "!! $descripcion: no se pudo resolver el principalId, se omite la asignacion de rol."
    return
  fi
  if az cosmosdb sql role assignment create \
      --account-name "$COSMOS_ACCOUNT" \
      --resource-group "$RESOURCE_GROUP" \
      --role-definition-name "Cosmos DB Built-in Data Contributor" \
      --principal-id "$principal_id" \
      --scope "$COSMOS_ID" \
      --output none 2>/tmp/cosmos_role_error.log; then
    echo "   OK Rol asignado a $descripcion."
  elif grep -qi "already exists\|Conflict" /tmp/cosmos_role_error.log 2>/dev/null; then
    echo "   OK Rol ya estaba asignado a $descripcion (se omite)."
  else
    echo "!! No se pudo confirmar la asignacion de rol a $descripcion -- revisar manualmente:"
    cat /tmp/cosmos_role_error.log 2>/dev/null || true
  fi
}

# Unicamente la Managed Identity del App Service recibe el rol de datos: nadie
# mas (ni desarrolladores con su propia sesion de az login) puede leer o
# escribir directamente en Cosmos. Un desarrollador corriendo la app localmente
# necesitaria que se le asigne este mismo rol a mano si el equipo decide
# permitirlo puntualmente; por defecto queda restringido solo a la app.
echo ">> Asignando rol de datos a la Managed Identity del App Service $APP_NAME..."
APP_PRINCIPAL_ID=$(az webapp identity show --name "$APP_NAME" --resource-group "$RESOURCE_GROUP" --query principalId -o tsv 2>/dev/null || echo "")
asignar_rol_cosmos "$APP_PRINCIPAL_ID" "la Managed Identity de $APP_NAME"

# ---------- 8. Salida Informativa ----------
echo ""
echo "==================== RESUMEN DE DESPLIEGUE ===================="
echo "Resource Group    : $RESOURCE_GROUP ($RG_LOCATION)"
echo "Cuenta Cosmos DB  : $COSMOS_ACCOUNT"
echo "Base de datos     : $COSMOS_DB"
echo "Contenedores      : transacciones (/accountId), scores (/transactionId)"
echo "Endpoint          : $COSMOS_ENDPOINT"
echo ""
echo "Agrega en application.properties (o como variables de entorno):"
echo "  spring.cloud.azure.cosmos.endpoint=$COSMOS_ENDPOINT"
echo "  spring.cloud.azure.cosmos.database=$COSMOS_DB"
echo "(sin spring.cloud.azure.cosmos.key: la app se autentica con az login / Managed Identity)"
echo "================================================================"
