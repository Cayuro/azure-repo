# Scripts de aprovisionamiento

Todos son genericos: se controlan por variables de entorno (`export VAR=...`
antes de correr el script). Las que son obligatorias fallan con un mensaje
claro si no las exportas; las opcionales tienen un valor por defecto
razonable.

No incluyen el App Service que calcula el score (eso ya existe / se
aprovisiona aparte). Cubren los 3 servicios que le faltan a este repo:

| Servicio | Script |
|---|---|
| Storage Queue (cola de fraude) | `sin-vnet/provision_storage_queue.sh` o `con-vnet/provision_storage_queue.sh` |
| Function App (email-sender) | `sin-vnet/provision_function_app.sh` o `con-vnet/provision_function_app.sh` |
| Email (Azure Communication Services) | `provision_email_service.sh` (uno solo, sin variante de VNet) |

## Por que Email no tiene variante con-vnet

Azure Communication Services / Email no soporta Private Endpoint hoy. La
llamada de la Function a ACS siempre sale por su endpoint publico, tengas o
no VNet Integration en el resto. Duplicar el script no cambiaria nada.

## sin-vnet vs con-vnet

- **sin-vnet**: todo por HTTPS publico, autenticado con Managed Identity +
  RBAC (sin claves). Mas simple y mas barato — usa el plan **Consumption**
  clasico para la Function (pago por ejecucion, sin costo fijo). Usa **una
  sola storage account**: la misma cuenta que tiene la cola de negocio sirve
  tambien como `AzureWebJobsStorage` de la Function, porque al no haber
  ningun bloqueo de red no hay conflicto en compartirla.

  **Ojo**: el plan Consumption dinamico para Linux no esta disponible en
  todas las regiones (ej. `chilecentral` no lo soporta, tira "Linux dynamic
  workers are not available"). Si te pasa eso, exporta `PLAN_NAME` con el
  nombre de un App Service Plan Linux que ya tengas (por ejemplo el mismo
  del App Service que calcula el score) y `provision_function_app.sh` la
  Function ahi en vez de Consumption — costo extra $0 si el plan ya existia.
- **con-vnet**: la Storage Queue queda con acceso publico bloqueado y solo
  alcanzable via Private Endpoint. La Function necesita VNet Integration
  regional para llegar a ella, lo que obliga a usar el plan **Flex
  Consumption** (Consumption clasico no soporta VNet Integration). Sigue
  siendo pago por ejecucion, no tiene el costo fijo de un plan Premium. Usa
  **dos storage accounts separadas** (una de runtime de la Function, otra de
  negocio) porque si compartieras la misma cuenta y la bloquearas, la
  creacion/despliegue de la Function fallaria.

**No mezclar variantes**: si la cola queda privada (`con-vnet`), la Function
tiene que usar tambien la variante `con-vnet` (si no, no la va a poder
alcanzar). Al reves si funciona pero no tiene sentido: una Function
`con-vnet` puede hablarle a una cola `sin-vnet` sin problema, solo que no
aporta nada.

## Orden sugerido

**sin-vnet** (el orden importa: la storage account tiene que existir antes
de crear la Function, porque comparten cuenta):

1. `provision_email_service.sh` — no depende de nada mas.
2. `sin-vnet/provision_storage_queue.sh` — crea la storage account + cola.
3. `sin-vnet/provision_function_app.sh` — reutiliza esa cuenta, crea la
   Function y se autoasigna el RBAC sobre la cola (no hace falta volver a
   correr el paso 2).

**con-vnet** (el orden no importa tanto porque cada script usa su propia
storage account, pero conviene este orden para tener el `PRINCIPAL_ID` a
mano):

1. `provision_email_service.sh` — no depende de nada mas.
2. `con-vnet/provision_function_app.sh` — te da el `PRINCIPAL_ID` de la
   Managed Identity de la Function.
3. `con-vnet/provision_storage_queue.sh` — pasale ese `PRINCIPAL_ID` en
   `CONSUMER_PRINCIPAL_ID` (y el del App Service en `PRODUCER_PRINCIPAL_ID`
   si ya lo tienes) para que el RBAC quede asignado en el mismo paso.

En ambos casos, con los valores que cada script imprime al final (nombre de
cola, `EmailSenderAddress`, `CommunicationServicesConnectionString`, etc.),
configuralos como *Application Settings* del Function App — ver la tabla en
`../README.md`.

## Ejemplo (variante sin-vnet)

```bash
export RG="rg-mi-proyecto"
export LOCATION="eastus"

export ACS_NAME="acs-email-sender"
export EMAIL_SERVICE_NAME="acs-email-service"
bash provision_email_service.sh

export STORAGE_ACCOUNT="stemailsenderqueue"
bash sin-vnet/provision_storage_queue.sh

export FUNCTION_APP_NAME="func-email-sender"
bash sin-vnet/provision_function_app.sh
# ya deja el RBAC de la Function sobre $STORAGE_ACCOUNT asignado
```

## Ejemplo (variante con-vnet)

```bash
export RG="rg-mi-proyecto"
export LOCATION="eastus"
export VNET_NAME="vnet-mi-proyecto"

export ACS_NAME="acs-email-sender"
export EMAIL_SERVICE_NAME="acs-email-service"
bash provision_email_service.sh

export FUNCTION_APP_NAME="func-email-sender"
bash con-vnet/provision_function_app.sh
# copia el Managed Identity: <principal-id>

export STORAGE_ACCOUNT="stemailsenderqueue"
export CONSUMER_PRINCIPAL_ID="<principal-id-de-arriba>"
bash con-vnet/provision_storage_queue.sh
```
