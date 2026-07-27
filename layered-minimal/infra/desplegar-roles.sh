#!/bin/bash

# 1. VARIABLES
SUBSCRIPTION_ID=$(az account show --query id --output tsv)
RESOURCE_GROUP="rg-centinela-prod"
SCOPE_RG="/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$RESOURCE_GROUP"

echo "=== Limpiando definiciones previas para evitar conflictos ==="
az role definition delete --name "Centinela - Administrador" --custom-role-only true 2>/dev/null
az role definition delete --name "Centinela - Analista de Fraude" --custom-role-only true 2>/dev/null
az role definition delete --name "Centinela - Auditor de Solo Lectura" --custom-role-only true 2>/dev/null
az role definition delete --name "Centinela - Servicio" --custom-role-only true 2>/dev/null

echo "=== Iniciando creación de Roles Personalizados ==="

# ----------------------------------------------------
# 1. ROL: ADMINISTRADOR
# ----------------------------------------------------
cat <<EOF > admin-role.json
{
  "Name": "Centinela - Administrador",
  "IsCustom": true,
  "Description": "Control total de la infraestructura en plano de control. Bloqueado en plano de datos.",
  "Actions": [
    "*"
  ],
  "NotActions": [
    "Microsoft.Authorization/*/Write",
    "Microsoft.Authorization/*/Delete"
  ],
  "DataActions": [],
  "NotDataActions": [
    "Microsoft.Storage/storageAccounts/blobServices/containers/blobs/*",
    "Microsoft.ServiceBus/namespaces/messages/*"
  ],
  "AssignableScopes": ["/subscriptions/$SUBSCRIPTION_ID"]
}
EOF
az role definition create --role-definition admin-role.json
echo "✔ Rol Administrador procesado."

# ----------------------------------------------------
# 2. ROL: ANALISTA DE FRAUDE (Operación Corregida de Azure)
# ----------------------------------------------------
cat <<EOF > analista-role.json
{
  "Name": "Centinela - Analista de Fraude",
  "IsCustom": true,
  "Description": "Lectura de infraestructura y acceso delegado temporal a documentos de identidad.",
  "Actions": [
    "Microsoft.Resources/subscriptions/resourceGroups/read",
    "Microsoft.Storage/storageAccounts/read"
  ],
  "NotActions": [
    "Microsoft.Storage/storageAccounts/write",
    "Microsoft.Network/*",
    "Microsoft.Compute/*"
  ],
  "DataActions": [
    "Microsoft.Storage/storageAccounts/blobServices/containers/blobs/read"
  ],
  "NotDataActions": [],
  "AssignableScopes": ["/subscriptions/$SUBSCRIPTION_ID"]
}
EOF
az role definition create --role-definition analista-role.json
echo "✔ Rol Analista de Fraude procesado."

# ----------------------------------------------------
# 3. ROL: AUDITOR DE SOLO LECTURA
# ----------------------------------------------------
cat <<EOF > auditor-role.json
{
  "Name": "Centinela - Auditor de Solo Lectura",
  "IsCustom": true,
  "Description": "Auditoría exclusiva del estado y configuraciones de los recursos de infraestructura.",
  "Actions": [
    "*/read"
  ],
  "NotActions": [
    "Microsoft.Authorization/*"
  ],
  "DataActions": [],
  "NotDataActions": [
    "*"
  ],
  "AssignableScopes": ["/subscriptions/$SUBSCRIPTION_ID"]
}
EOF
az role definition create --role-definition auditor-role.json
echo "✔ Rol Auditor procesado."

# ----------------------------------------------------
# 4. ROL: SERVICIO
# ----------------------------------------------------
cat <<EOF > servicio-role.json
{
  "Name": "Centinela - Servicio",
  "IsCustom": true,
  "Description": "Permisos exclusivos de plano de datos para la ingesta de transacciones y carga de archivos.",
  "Actions": [
    "Microsoft.Storage/storageAccounts/read",
    "Microsoft.ServiceBus/namespaces/read"
  ],
  "NotActions": [
    "Microsoft.Resources/*",
    "Microsoft.Storage/storageAccounts/write"
  ],
  "DataActions": [
    "Microsoft.Storage/storageAccounts/blobServices/containers/blobs/write",
    "Microsoft.ServiceBus/namespaces/messages/send/action",
    "Microsoft.ServiceBus/namespaces/messages/receive/action"
  ],
  "NotDataActions": [],
  "AssignableScopes": ["/subscriptions/$SUBSCRIPTION_ID"]
}
EOF
az role definition create --role-definition servicio-role.json
echo "✔ Rol Servicio procesado."

# Limpieza de JSONs temporales
rm admin-role.json analista-role.json auditor-role.json servicio-role.json

echo "⏳ Esperando 15 segundos para la propagación de roles en la región de Azure..."
sleep 15

echo -e "\n=== Iniciando Asignación de Roles por IDs ==="

# IDs manuales asignados directamente
ID_JUAN="83596ca4-8299-42b7-9b98-656e1af08b0a"
ID_SPIEDRAHITA="2e2e2801-dda7-4bf4-a640-30048c936c63"
ID_ANTONIO="747d7260-85d2-4d60-a951-c1c15fb51993"

# Ejecución de asignaciones directas
az role assignment create --assignee "$ID_JUAN" --role "Centinela - Administrador" --scope "$SCOPE_RG"
az role assignment create --assignee "$ID_SPIEDRAHITA" --role "Centinela - Analista de Fraude" --scope "$SCOPE_RG"
az role assignment create --assignee "$ID_ANTONIO" --role "Centinela - Auditor de Solo Lectura" --scope "$SCOPE_RG"

echo "✔ Proceso finalizado con éxito."
