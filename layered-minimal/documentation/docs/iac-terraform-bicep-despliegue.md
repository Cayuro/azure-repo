# Proyecto Centinela — Infraestructura como Código (Terraform y Bicep)

> Este archivo **simula** cómo se vería el despliegue del proyecto usando Infrastructure as Code (IaC), como alternativa a los scripts `az cli` existentes en `layered-minimal/infra/*.sh`.
> El código Terraform/Bicep aquí replica **exactamente** los mismos recursos, nombres, SKUs y controles de seguridad que ya están en los scripts bash y en `documentation/docs-network/`, para que sea un reemplazo 1:1, no una reinvención.
>
> ⚠️ Este código no ha sido ejecutado contra una suscripción real (es una simulación/plantilla lista para adaptar). Antes de aplicarlo en producción, revísalo con el equipo de infraestructura y ajusta nombres/valores según el entorno real.

---

## 0. Qué recursos se despliegan

| Recurso | Nombre (igual que en los scripts bash) | Origen |
|---|---|---|
| Resource Group | `rg-centinela-prod` | `provision_app_service.sh` / `provision_storage_account.sh` |
| Virtual Network | `vnet-centinela-prod-v3` (`10.10.0.0/16`) | `network-design.md` |
| Subred App | `snet-app-prod` (`10.10.1.0/27`), delegada a `Microsoft.Web/serverFarms` | `provision_app_service.sh` |
| Subred Data | `snet-data-prod` (`10.10.2.0/27`) — reservada, sin recursos aún | `network-design.md` |
| Subred Process | `snet-process-prod` (`10.10.3.0/27`) — reservada, sin recursos aún | `network-design.md` |
| App Service Plan | `asp-centinela-prod` (SKU `B1`, Linux) | `provision_app_service.sh` |
| App Service (Web App) | `appcentinelaprodgrupo3` (runtime `JAVA|21-java21`) | `provision_app_service.sh` |
| Managed Identity | System-Assigned, en el App Service | `provision_app_service.sh` |
| VNet Integration | App Service ↔ `snet-app-prod` | `provision_app_service.sh` |
| Storage Account | `sttransaccionesfase1` (`Standard_LRS`, sin llaves, firewall `Deny`) | `provision_storage_account.sh` |
| Contenedor Blob | `evidencias-financieras-privado` (privado) | `provision_storage_account.sh` |
| Colas | `cola-transacciones-ingesta` + `cola-transacciones-ingesta-poison` | `provision_storage_account.sh` |
| Role Assignments | `Storage Blob Data Contributor` + `Storage Queue Data Contributor` sobre la Managed Identity del App Service | `provision_storage_account.sh` |

---

## 1. Terraform

Estructura de archivos sugerida:

```
infra-terraform/
├── main.tf
├── variables.tf
├── outputs.tf
└── providers.tf
```

### 1.1 `providers.tf`

```hcl
terraform {
  required_version = ">= 1.7.0"
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.100"
    }
  }
}

provider "azurerm" {
  features {
    storage {
      # Evita que Terraform intente autenticarse con account key en operaciones data-plane
      shared_access_key_enabled = false
    }
  }
}
```

### 1.2 `variables.tf`

```hcl
variable "project" {
  type    = string
  default = "centinela"
}

variable "environment" {
  type    = string
  default = "prod"
}

variable "location" {
  description = "Region de computo y red (App Service, VNet)"
  type        = string
  default     = "chilecentral"
}

variable "app_name" {
  type    = string
  default = "appcentinelaprodgrupo3"
}

variable "storage_account_name" {
  description = "Debe ser globalmente unico, solo minusculas y numeros, 3-24 caracteres"
  type        = string
  default     = "sttransaccionesfase1"
}

variable "app_service_sku" {
  type    = string
  default = "B1"
}

variable "risk_merchant_categories" {
  type    = list(string)
  default = ["gambling", "crypto", "cash_advance"]
}
```

### 1.3 `main.tf`

```hcl
# ---------- Resource Group ----------
resource "azurerm_resource_group" "rg" {
  name     = "rg-${var.project}-${var.environment}"
  location = var.location
}

# ---------- Red Virtual y Subredes ----------
resource "azurerm_virtual_network" "vnet" {
  name                = "vnet-${var.project}-${var.environment}-v3"
  address_space       = ["10.10.0.0/16"]
  location            = azurerm_resource_group.rg.location
  resource_group_name = azurerm_resource_group.rg.name
}

resource "azurerm_subnet" "snet_app" {
  name                 = "snet-app-prod"
  resource_group_name  = azurerm_resource_group.rg.name
  virtual_network_name = azurerm_virtual_network.vnet.name
  address_prefixes     = ["10.10.1.0/27"]

  delegation {
    name = "app-service-delegation"
    service_delegation {
      name    = "Microsoft.Web/serverFarms"
      actions = ["Microsoft.Network/virtualNetworks/subnets/action"]
    }
  }

  service_endpoints = ["Microsoft.Storage"]
}

resource "azurerm_subnet" "snet_data" {
  name                 = "snet-data-prod"
  resource_group_name  = azurerm_resource_group.rg.name
  virtual_network_name = azurerm_virtual_network.vnet.name
  address_prefixes     = ["10.10.2.0/27"]
  # Reservada para Azure SQL / Private Endpoints (Sprint futuro)
}

resource "azurerm_subnet" "snet_process" {
  name                 = "snet-process-prod"
  resource_group_name  = azurerm_resource_group.rg.name
  virtual_network_name = azurerm_virtual_network.vnet.name
  address_prefixes     = ["10.10.3.0/27"]
  # Reservada para Azure Functions / procesamiento en segundo plano (Sprint futuro)
}

# ---------- App Service Plan (Linux, B1) ----------
resource "azurerm_service_plan" "asp" {
  name                = "asp-${var.project}-${var.environment}"
  resource_group_name = azurerm_resource_group.rg.name
  location            = azurerm_resource_group.rg.location
  os_type             = "Linux"
  sku_name            = var.app_service_sku
}

# ---------- App Service (Web App) con Managed Identity ----------
resource "azurerm_linux_web_app" "app" {
  name                = var.app_name
  resource_group_name = azurerm_resource_group.rg.name
  location            = azurerm_resource_group.rg.location
  service_plan_id     = azurerm_service_plan.asp.id

  site_config {
    application_stack {
      java_version        = "21"
      java_server         = "JAVA"
      java_server_version = "21"
    }
  }

  identity {
    type = "SystemAssigned"
  }

  app_settings = {
    "AZURE_STORAGE_ACCOUNT_NAME"        = var.storage_account_name
    "azure.storage.account-name"        = var.storage_account_name
    "azure.storage.container-name"      = "evidencias-financieras-privado"
    "ingesta.scoring.threshold"         = "60"
    "ingesta.scoring.risk-merchant-categories" = join(",", var.risk_merchant_categories)
  }
}

# ---------- Integracion VNet del App Service ----------
resource "azurerm_app_service_virtual_network_swift_connection" "vnet_integration" {
  app_service_id = azurerm_linux_web_app.app.id
  subnet_id      = azurerm_subnet.snet_app.id
}

# ---------- Storage Account (sin llaves, firewall en Deny) ----------
resource "azurerm_storage_account" "storage" {
  name                     = var.storage_account_name
  resource_group_name      = azurerm_resource_group.rg.name
  location                 = azurerm_resource_group.rg.location
  account_tier             = "Standard"
  account_replication_type = "LRS"
  account_kind             = "StorageV2"

  min_tls_version                  = "TLS1_2"
  https_traffic_only_enabled       = true
  allow_nested_items_to_be_public  = false
  shared_access_key_enabled        = false # equivalente a --allow-shared-key-access false

  network_rules {
    default_action             = "Deny"
    virtual_network_subnet_ids = [azurerm_subnet.snet_app.id]
    bypass                     = ["AzureServices"]
  }
}

# ---------- Contenedor de Blobs (evidencias) ----------
resource "azurerm_storage_container" "evidencias" {
  name                  = "evidencias-financieras-privado"
  storage_account_name  = azurerm_storage_account.storage.name
  container_access_type = "private"
}

# ---------- Colas de ingesta ----------
resource "azurerm_storage_queue" "ingesta" {
  name                 = "cola-transacciones-ingesta"
  storage_account_name = azurerm_storage_account.storage.name
}

resource "azurerm_storage_queue" "ingesta_poison" {
  name                 = "cola-transacciones-ingesta-poison"
  storage_account_name = azurerm_storage_account.storage.name
}

# ---------- RBAC: Managed Identity del App Service sobre el Storage ----------
resource "azurerm_role_assignment" "blob_contributor" {
  scope                = azurerm_storage_account.storage.id
  role_definition_name = "Storage Blob Data Contributor"
  principal_id         = azurerm_linux_web_app.app.identity[0].principal_id
}

resource "azurerm_role_assignment" "queue_contributor" {
  scope                = azurerm_storage_account.storage.id
  role_definition_name = "Storage Queue Data Contributor"
  principal_id         = azurerm_linux_web_app.app.identity[0].principal_id
}
```

### 1.4 `outputs.tf`

```hcl
output "app_service_url" {
  value = "https://${azurerm_linux_web_app.app.default_hostname}"
}

output "app_service_principal_id" {
  value = azurerm_linux_web_app.app.identity[0].principal_id
}

output "storage_blob_endpoint" {
  value = azurerm_storage_account.storage.primary_blob_endpoint
}
```

### 1.5 Cómo desplegar con Terraform

```bash
cd infra-terraform

# 1. Autenticarse (usa tu sesion de az login o un Service Principal)
az login

# 2. Inicializar el backend y providers
terraform init

# 3. Ver el plan de cambios antes de aplicar (¡siempre revisar!)
terraform plan -out=tfplan

# 4. Aplicar
terraform apply tfplan

# 5. Ver outputs (URL de la app, principalId de la Managed Identity, endpoint del blob)
terraform output

# Para destruir el entorno (con cuidado, es producción)
terraform destroy
```

---

## 2. Bicep

Estructura de archivos sugerida:

```
infra-bicep/
└── main.bicep
```

### 2.1 `main.bicep`

```bicep
@description('Nombre del proyecto')
param project string = 'centinela'

@description('Ambiente')
param environment string = 'prod'

@description('Region de computo y red (App Service, VNet)')
param location string = 'chilecentral'

@description('Nombre del App Service')
param appName string = 'appcentinelaprodgrupo3'

@description('Nombre del Storage Account (unico globalmente, minusculas y numeros)')
param storageAccountName string = 'sttransaccionesfase1'

@description('SKU del App Service Plan')
param appServiceSku string = 'B1'

var resourceGroupPrefix = 'rg-${project}-${environment}'
var vnetName = 'vnet-${project}-${environment}-v3'
var appServicePlanName = 'asp-${project}-${environment}'

// ---------- Red Virtual ----------
resource vnet 'Microsoft.Network/virtualNetworks@2023-09-01' = {
  name: vnetName
  location: location
  properties: {
    addressSpace: {
      addressPrefixes: ['10.10.0.0/16']
    }
    subnets: [
      {
        name: 'snet-app-prod'
        properties: {
          addressPrefix: '10.10.1.0/27'
          delegations: [
            {
              name: 'app-service-delegation'
              properties: {
                serviceName: 'Microsoft.Web/serverFarms'
              }
            }
          ]
          serviceEndpoints: [
            { service: 'Microsoft.Storage' }
          ]
        }
      }
      {
        name: 'snet-data-prod'
        properties: {
          addressPrefix: '10.10.2.0/27'
        }
      }
      {
        name: 'snet-process-prod'
        properties: {
          addressPrefix: '10.10.3.0/27'
        }
      }
    ]
  }
}

// ---------- App Service Plan (Linux) ----------
resource appServicePlan 'Microsoft.Web/serverfarms@2023-12-01' = {
  name: appServicePlanName
  location: location
  sku: {
    name: appServiceSku
    tier: 'Basic'
  }
  kind: 'linux'
  properties: {
    reserved: true
  }
}

// ---------- App Service con Managed Identity ----------
resource webApp 'Microsoft.Web/sites@2023-12-01' = {
  name: appName
  location: location
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    serverFarmId: appServicePlan.id
    virtualNetworkSubnetId: '${vnet.id}/subnets/snet-app-prod'
    siteConfig: {
      linuxFxVersion: 'JAVA|21-java21'
      appSettings: [
        { name: 'azure.storage.account-name', value: storageAccountName }
        { name: 'azure.storage.container-name', value: 'evidencias-financieras-privado' }
        { name: 'ingesta.scoring.threshold', value: '60' }
      ]
    }
  }
}

// ---------- Storage Account (sin llaves, firewall en Deny) ----------
resource storageAccount 'Microsoft.Storage/storageAccounts@2023-01-01' = {
  name: storageAccountName
  location: location
  kind: 'StorageV2'
  sku: {
    name: 'Standard_LRS'
  }
  properties: {
    minimumTlsVersion: 'TLS1_2'
    supportsHttpsTrafficOnly: true
    allowBlobPublicAccess: false
    allowSharedKeyAccess: false
    networkAcls: {
      defaultAction: 'Deny'
      bypass: 'AzureServices'
      virtualNetworkRules: [
        {
          id: '${vnet.id}/subnets/snet-app-prod'
          action: 'Allow'
        }
      ]
    }
  }
}

// ---------- Contenedor de Blobs (evidencias) ----------
resource blobService 'Microsoft.Storage/storageAccounts/blobServices@2023-01-01' = {
  parent: storageAccount
  name: 'default'
}

resource evidenciasContainer 'Microsoft.Storage/storageAccounts/blobServices/containers@2023-01-01' = {
  parent: blobService
  name: 'evidencias-financieras-privado'
  properties: {
    publicAccess: 'None'
  }
}

// ---------- Colas de ingesta ----------
resource queueService 'Microsoft.Storage/storageAccounts/queueServices@2023-01-01' = {
  parent: storageAccount
  name: 'default'
}

resource colaIngesta 'Microsoft.Storage/storageAccounts/queueServices/queues@2023-01-01' = {
  parent: queueService
  name: 'cola-transacciones-ingesta'
}

resource colaIngestaPoison 'Microsoft.Storage/storageAccounts/queueServices/queues@2023-01-01' = {
  parent: queueService
  name: 'cola-transacciones-ingesta-poison'
}

// ---------- RBAC: Storage Blob Data Contributor ----------
var storageBlobDataContributorRoleId = 'ba92f5b4-2d11-453d-a403-e96b0029c9fe'
resource blobRoleAssignment 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(storageAccount.id, webApp.id, storageBlobDataContributorRoleId)
  scope: storageAccount
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', storageBlobDataContributorRoleId)
    principalId: webApp.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

// ---------- RBAC: Storage Queue Data Contributor ----------
var storageQueueDataContributorRoleId = '974c5e8b-45b9-4653-ba55-5f855dd0fb88'
resource queueRoleAssignment 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(storageAccount.id, webApp.id, storageQueueDataContributorRoleId)
  scope: storageAccount
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', storageQueueDataContributorRoleId)
    principalId: webApp.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

output appServiceUrl string = 'https://${webApp.properties.defaultHostName}'
output appServicePrincipalId string = webApp.identity.principalId
output storageBlobEndpoint string = storageAccount.properties.primaryEndpoints.blob
```

### 2.2 Cómo desplegar con Bicep

```bash
# 1. Autenticarse
az login
az account set --subscription "<subscription-id>"

# 2. Crear el Resource Group (Bicep no lo crea, se despliega DENTRO de uno existente)
az group create --name rg-centinela-prod --location chilecentral

# 3. Validar la plantilla antes de desplegar (dry-run)
az deployment group what-if \
  --resource-group rg-centinela-prod \
  --template-file infra-bicep/main.bicep

# 4. Desplegar
az deployment group create \
  --resource-group rg-centinela-prod \
  --template-file infra-bicep/main.bicep \
  --parameters appName=appcentinelaprodgrupo3 storageAccountName=sttransaccionesfase1

# 5. Ver outputs
az deployment group show \
  --resource-group rg-centinela-prod \
  --name main \
  --query properties.outputs
```

---

## 3. Terraform vs Bicep vs los scripts `az cli` actuales — cuándo usar cada uno

| Aspecto | Scripts `az cli` (actuales) | Terraform | Bicep |
|---|---|---|---|
| Estado del despliegue | No hay estado; cada corrida es imperativa (`|| true` para tolerar "ya existe") | Estado explícito (`terraform.tfstate`) — sabe qué existe y qué cambió | Sin estado propio; usa el estado real de Azure (`what-if` para comparar) |
| Multi-nube | N/A, ya es Azure-only | Sí (mismo lenguaje sirve para AWS/GCP) | No, 100% Azure |
| Curva de aprendizaje | Baja (bash + az cli) | Media (HCL propio) | Media-baja (extensión natural de ARM/JSON) |
| Ideal para | Scripts rápidos, demos, pipelines simples | Equipos multi-cloud o que ya usan Terraform en otros proyectos | Equipos 100% Azure que quieren la herramienta "nativa" de Microsoft |
| Rollback / drift detection | Manual | `terraform plan` detecta drift automáticamente | `what-if` detecta drift antes de aplicar |

**Recomendación para este proyecto:** dado que hoy todo vive en Azure y el equipo ya usa `az cli` directamente, **Bicep** es el camino de menor fricción para migrar de scripts imperativos a IaC declarativo (misma CLI, sintaxis más cercana a lo que ya conocen). **Terraform** es la opción correcta si el equipo anticipa portabilidad multi-nube a futuro o ya tiene tooling de Terraform en otros proyectos de la organización.
