# email-sender-function

Azure Function (Java 21, Maven) que consume `FraudAlertEvent` desde una Azure
Storage Queue y envia la alerta por correo usando Azure Communication Services
(ACS) - Email.

Ver `../Azure_Email_Function_Architecture.txt` para la arquitectura completa.

## Que falta crear en Azure (aun no existe el servicio de Email)

Se necesitan **dos recursos** dentro de Azure Communication Services:

1. Un recurso `Communication Services` (el que da el *connection string*).
2. Un recurso `Email Communication Services` con un dominio. Como pediste
   usar el dominio **por defecto de Azure** (sin DNS propio), se usa el tipo
   `AzureManagedDomain`: Azure genera un subdominio tipo
   `xxxxxxxx-xxxx-....azurecomm.net` ya verificado, sin que tengas que tocar
   tu DNS. La limitacion es que el remitente queda fijo como
   `DoNotReply@<ese-subdominio>.azurecomm.net` (puedes agregar mas alias con
   `sender-username create`, pero no puedes usar tu propio dominio de correo).

### Pasos (Azure CLI)

```bash
# Variables (ajustar)
RG="rg-centinela-prod"
LOCATION="global"                 # Communication Services es un recurso "global"
DATA_LOCATION="United States"     # Residencia de datos: United States | Europe | UK | Brazil | Asia Pacific | Australia | Canada | ...
ACS_NAME="acs-centinela-prod"
EMAIL_SERVICE_NAME="acs-email-centinela-prod"

# 0. Extension de CLI (si no la tienes)
az extension add --name communication

# 1. Resource Group (si no existe)
az group create --name "$RG" --location "eastus"

# 2. Recurso Communication Services (da el connection string)
az communication create \
  --name "$ACS_NAME" \
  --location "$LOCATION" \
  --data-location "$DATA_LOCATION" \
  --resource-group "$RG"

# 3. Recurso Email Communication Services
az communication email create \
  --name "$EMAIL_SERVICE_NAME" \
  --location "$LOCATION" \
  --data-location "$DATA_LOCATION" \
  --resource-group "$RG"

# 4. Dominio administrado por Azure (default, sin DNS propio)
az communication email domain create \
  --domain-name "AzureManagedDomain" \
  --email-service-name "$EMAIL_SERVICE_NAME" \
  --resource-group "$RG" \
  --location "$LOCATION" \
  --domain-management "AzureManaged"

# 5. Obtener el id del dominio y el subdominio generado
az communication email domain show \
  --domain-name "AzureManagedDomain" \
  --email-service-name "$EMAIL_SERVICE_NAME" \
  --resource-group "$RG" \
  --query "{id:id, fromSenderDomain:fromSenderDomain, mailFromSenderDomain:mailFromSenderDomain}" \
  -o json

# 6. Vincular el dominio al recurso de Communication Services
DOMAIN_ID=$(az communication email domain show \
  --domain-name "AzureManagedDomain" \
  --email-service-name "$EMAIL_SERVICE_NAME" \
  --resource-group "$RG" \
  --query "id" -o tsv)

az communication update \
  --name "$ACS_NAME" \
  --resource-group "$RG" \
  --linked-domains "$DOMAIN_ID"

# 7. Obtener el connection string (esto va en CommunicationServicesConnectionString)
az communication list-key \
  --name "$ACS_NAME" \
  --resource-group "$RG" \
  --query "primaryConnectionString" -o tsv
```

El remitente por defecto queda como `DoNotReply@<fromSenderDomain>` (el valor
que devolvio el paso 5). Ese valor es el que va en `EmailSenderAddress`.

> El dominio administrado por Azure tiene limite de envio (pensado para dev/
> pruebas y volumenes bajos). Si mas adelante el volumen de alertas crece,
> se migra a un dominio propio verificado por DNS sin tocar el codigo de la
> Function (solo cambia `EmailSenderAddress` y no hace falta el paso 4).

## Variables de entorno / App Settings que necesita la Function

| Nombre | Descripcion |
|---|---|
| `AzureWebJobsStorage` | Connection string de la Storage Account donde vive la cola. Debe ser la **misma cuenta** donde el App Service publica los `FraudAlertEvent`. |
| `FraudQueueName` | Nombre de la cola que consume el Queue Trigger (la misma que usa el App Service para publicar). |
| `CommunicationServicesConnectionString` | Connection string obtenido en el paso 7. |
| `EmailSenderAddress` | Remitente, ej. `DoNotReply@xxxxxxxx.azurecomm.net` (paso 5). |
| `FraudAlertRecipients` | Lista de destinatarios separados por coma. |

En local, se configuran en `local.settings.json` (no se commitea, esta en
`.gitignore`). En Azure, se configuran como *Application Settings* del
Function App (idealmente referenciando Key Vault para el connection string).

## Correr localmente

Requiere [Azure Functions Core Tools v4](https://learn.microsoft.com/azure/azure-functions/functions-run-local)
y Java 21.

```bash
mvn clean package
mvn azure-functions:run
```

Para probar sin depender del App Service real, se puede encolar un mensaje de
prueba directamente en la Storage Queue local (Azurite) con el JSON de un
`FraudAlertEvent`.

## Desplegar

```bash
mvn clean package
mvn azure-functions:deploy
```

Ajusta antes `functionAppName`, `functionResourceGroup` y `functionAppRegion`
en el `pom.xml` (o pasalos como `-D` al comando), y asegurate de que el
Function App ya exista o que la identidad usada para el deploy tenga permisos
para crearlo.
