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

### Como crearlo

Script listo para correr: [`infra/provision_email_service.sh`](infra/provision_email_service.sh)
(genérico, solo hay que exportar `RG`, `ACS_NAME`, `EMAIL_SERVICE_NAME` y
opcionalmente `DATA_LOCATION`). Al final imprime `EmailSenderAddress` y
`CommunicationServicesConnectionString` listos para pegar en los App
Settings de la Function.

El remitente por defecto queda como `DoNotReply@<subdominio-generado>`.

> El dominio administrado por Azure tiene limite de envio (pensado para dev/
> pruebas y volumenes bajos). Si mas adelante el volumen de alertas crece,
> se migra a un dominio propio verificado por DNS sin tocar el codigo de la
> Function (solo cambia `EmailSenderAddress`).

## Networking: como se conecta la Function con la cola (y con ACS)

No es obligatorio usar VNet: por defecto todo pasa por HTTPS publico con
Managed Identity + RBAC. La VNet solo hace falta si la Storage Account de
negocio (la que tiene la cola) esta (o va a estar) con acceso publico
bloqueado. Hay dos variantes completas de scripts en `infra/`, ver
[`infra/README.md`](infra/README.md) para el detalle y el orden de
ejecucion:

- **`infra/sin-vnet/`**: todo publico, Function en plan Consumption clasico.
  Usa **una sola storage account**: la misma cuenta que tiene la cola de
  negocio sirve tambien como `AzureWebJobsStorage` (plomeria interna de la
  Function: deployment package, leases de coordinacion). No hay problema en
  compartirla porque nada queda bloqueado a nivel de red.
- **`infra/con-vnet/`**: la cola queda bloqueada a acceso publico y solo
  alcanzable via **Private Endpoint** (subrecurso `queue`) + Private DNS
  Zone. La Function llega a ella por **VNet Integration** regional (subred
  delegada, plan **Flex Consumption**, unico que soporta VNet Integration
  con pago por ejecucion) con Route All activado, autenticada sin claves via
  **Managed Identity** (`FraudQueueStorage__queueServiceUri` +
  `FraudQueueStorage__credential=managedidentity`, rol RBAC `Storage Queue
  Data Contributor`). Aqui **si hacen falta dos storage accounts
  separadas** (una nueva para el runtime de la Function, otra para la cola
  de negocio): si usaras la misma cuenta para las dos cosas y la
  bloquearas, la creacion/despliegue de la Function fallaria porque Azure
  no podria subir el paquete de deployment a una cuenta sin acceso publico.

**Azure Communication Services (Email) se queda fuera de la VNet en ambos
casos.** Hoy no soporta Private Endpoint, asi que esa llamada siempre sale
por el endpoint publico de ACS. No es un fallo de configuracion, es una
limitacion actual del servicio.

Los scripts de la variante con-vnet tienen comandos marcados como
"verificar" en su encabezado (sintaxis de Flex Consumption relativamente
nueva) - leelos antes de correrlos.

## Encoding de los mensajes de la cola

`host.json` fija `extensions.queues.messageEncoding = "none"`. Es
importante: por defecto el host de Azure Functions espera que el contenido
de la cola venga en **Base64** (herencia del SDK antiguo de .NET). Si el
productor escribe texto plano, el host falla con
`"Message decoding has failed! Check MessageEncoding settings"`, descarta el
mensaje tras `maxDequeueCount` reintentos y lo manda a la cola de poison
**sin llegar a invocar la Function** (no aparece ni un log de la app, lo que
lo hace confuso de diagnosticar).

Con `"none"` el mensaje se lee como texto plano UTF-8, que es lo que
escriben tanto `az storage message put` como el SDK de Java
(`QueueClient.sendMessage(String)`) que usa el App Service productor.

## Variables de entorno / App Settings que necesita la Function

| Nombre | Descripcion |
|---|---|
| `AzureWebJobsStorage` | Connection string de la storage account de runtime de la Function. La configura sola `az functionapp create` / el script de infra. En `sin-vnet` es la misma cuenta que la de negocio; en `con-vnet` es una cuenta separada (ver sección de Networking). |
| `FraudQueueStorage` | Conexion (por Managed Identity si usas el script de infra, o connection string en local) hacia la storage account **de negocio** donde vive la cola. Es la que usa el `@QueueTrigger`. |
| `FraudQueueName` | Nombre de la cola que consume el Queue Trigger (la misma que usa el App Service para publicar). |
| `CommunicationServicesConnectionString` | Connection string que imprime `infra/provision_email_service.sh` al final. |
| `EmailSenderAddress` | Remitente, ej. `DoNotReply@xxxxxxxx.azurecomm.net` (tambien lo imprime ese script). |
| `FraudAlertRecipients` | Lista de destinatarios separados por coma. |

En local, se configuran en `local.settings.json` (no se commitea, esta en
`.gitignore`). En Azure, se configuran como *Application Settings* del
Function App (idealmente referenciando Key Vault para el connection string
de ACS).

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
