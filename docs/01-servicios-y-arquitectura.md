# Servicios, arquitectura y decisiones

Sistema de notificación por correo de alertas de fraude: cuando el motor de
scoring detecta una transacción sospechosa, se envía un correo a los
analistas.

## Flujo

```
App Service (Spring Boot)          ← lo mantiene el equipo de scoring
   │  calcula el score
   │  si score >= threshold, publica un FraudAlertEvent
   ▼
Azure Storage Queue                ← cola-casos-fraude
   │  Queue Trigger
   ▼
Azure Function (Java 21)           ← este repo
   │  deserializa, construye asunto + HTML
   ▼
Azure Communication Services       ← Email, dominio administrado
   │
   ▼
Correo al analista
```

La cola desacopla las dos mitades: el App Service no sabe nada de correos ni
de plantillas HTML, y la Function no sabe nada de scoring. Si el envío falla,
la cola reintenta sola (5 veces) y luego aparta el mensaje en una cola de
"poison" para inspección, sin perder nada y sin bloquear el scoring.

## Orden de creación

El orden importa: hay dependencias reales entre los scripts.

| # | Servicio | Script | Por qué en esa posición |
|---|---|---|---|
| 1 | Storage Account + colas | `scripts/01-storage-queue.sh` | La Function la usa como `AzureWebJobsStorage`, y `az functionapp create` exige que ya exista. |
| 2 | Communication Services (Email) | `scripts/02-communication-services.sh` | Independiente, pero su salida (remitente y connection string) se necesita en el paso 5. |
| 3 | App Service Plan | `scripts/03-app-service-plan.sh` | La Function App necesita un plan donde correr. |
| 4 | Function App | `scripts/04-function-app.sh` | Depende de 1 y 3. Aquí se crea la Managed Identity y se le da RBAC sobre la cola. |
| 5 | App Settings | `scripts/05-app-settings.sh` | Necesita que la Function App exista y los datos del paso 2. |
| 6 | Desplegar el código | `mvn azure-functions:deploy` | Último: sube el JAR a la Function ya configurada. |

Preparación previa:

```bash
cp docs/.env.example docs/.env    # editar con tus nombres/región
az login
```

Los scripts cargan `docs/.env` solos. Todos son idempotentes: volver a
correrlos con los mismos valores no rompe nada.

## Los servicios, uno por uno

### 1. Azure Storage Account + Queues

**Qué es**: `stcolafraudecentinela`, con dos colas: `cola-casos-fraude` y
`cola-casos-fraude-poison`.

**Para qué**: transporta los eventos entre el App Service y la Function, y
desacopla ambos. Si la Function está caída, los mensajes esperan en la cola
(hasta 7 días) en vez de perderse.

**Cómo habla con los demás**: el App Service escribe con el SDK de Java
autenticado por Managed Identity; la Function lee mediante el Queue Trigger,
también por Managed Identity. Nadie usa claves ni connection strings.

**Cola de poison**: si un mensaje falla 5 veces (`maxDequeueCount` en
`host.json`), el host lo mueve ahí automáticamente. Es el primer lugar donde
mirar cuando "no llegó el correo".

### 2. Azure Communication Services — Email

**Qué es**: dos recursos (`Communication Services` + `Email Communication
Services`) más un dominio `AzureManagedDomain`.

**Para qué**: es el servicio que efectivamente entrega el correo (SMTP,
reputación de IP, reintentos de entrega). Sin él habría que montar un SMTP
propio o contratar un tercero.

**Cómo habla con los demás**: la Function lo llama por HTTPS con el SDK
`azure-communication-email`, autenticándose con un connection string
guardado en los App Settings.

**Remitente**: `DoNotReply@<subdominio>.azurecomm.net`, generado por Azure.

### 3. App Service Plan

**Qué es**: `asp-func-email-sender`, plan Linux B1 dedicado.

**Para qué**: es el cómputo donde corre la Function. Ver decisiones 1 y 4:
no fue la primera opción, pero sí la que funciona en esta región.

**Costo**: fijo mensual, no por ejecución. Es el único recurso de este
sistema con costo relevante.

### 4. Function App

**Qué es**: `func-email-sender-centinela`, Java 21, runtime de Functions v4.

**Para qué**: contiene la lógica: consumir la cola, construir el asunto y el
HTML del correo, y llamar a ACS.

**Cómo habla con los demás**: entra por Queue Trigger (Storage), sale por
HTTPS (ACS). Su Managed Identity tiene `Storage Queue Data Contributor`
sobre la Storage Account.

### 5. Application Insights

**Qué es**: se crea solo junto con la Function App, no hay script.

**Para qué**: es la única forma práctica de ver qué pasa dentro. Los logs de
`az webapp log tail` solo muestran eventos de plataforma (contenedor
arrancando/parando); las trazas de la aplicación y del host viven aquí.

Consultarlo con `emailSender/email-sender-function/samples/query_logs.sh`.

## Decisiones y alternativas descartadas

### 1. Plan Dedicated (B1) en vez de Consumption

**Lo natural** para una función que se dispara por eventos es el plan
**Consumption**: pago por ejecución, coste ~$0 con volumen bajo.

**Por qué no**: `chilecentral` no lo soporta para Linux. `az functionapp
create --consumption-plan-location chilecentral` falla con *"Linux dynamic
workers are not available in resource group"*. Es una región nueva sin ese
plan habilitado.

**Alternativas evaluadas**:
- *Flex Consumption*: también pago por ejecución y sí soporta VNet, pero su
  disponibilidad por región es aún más limitada.
- *Crear la Function en otra región* (ej. `eastus`): funciona, pero deja el
  cómputo lejos de la cola y del resto del sistema, sin ganancia real.
- *Elastic Premium (EP1)*: ~US$125-150/mes fijos. Descartado por costo: el
  proyecto corre con el crédito de prueba de US$200.

**Resultado**: plan Dedicated B1, que sí existe en la región.

### 2. Java puro con Maven, sin Spring en la Function

**Alternativa**: Spring Cloud Function con el adapter de Azure, para reusar
el estilo del App Service.

**Por qué no**: arrancar un `ApplicationContext` completo en cada cold start
cuesta segundos; el adapter suele ir rezagado frente a versiones recientes
de Spring Boot; y con `Function<T,R>` se pierde acceso al `ExecutionContext`
(útil para el dequeue count y el logger del host). Para una función con un
solo trigger, la portabilidad multi-cloud que ofrece no compensa.

### 3. Una sola Storage Account, no dos

Toda Function App necesita una Storage Account (`AzureWebJobsStorage`) para
su propio funcionamiento: paquete de despliegue y coordinación entre
instancias. Es un requisito de la plataforma, no del proyecto.

Se usa **la misma cuenta** que la cola de negocio. Tendría sentido
separarlas si la cuenta de negocio estuviera cerrada a acceso público
(entonces el despliegue no podría subir el paquete), pero no es el caso.

### 4. Plan dedicado, no compartido con el App Service de scoring

**Primer intento**: reutilizar `asp-centinela-prod`, el plan que ya usa el
App Service. Coste adicional: $0.

**Por qué no funcionó**: un B1 tiene ~1.75 GB de RAM. Con dos JVMs
compitiendo (el App Service y la Function), el contenedor de la Function
empezó a reiniciarse de forma errática, arrancando y deteniéndose solo cada
pocos minutos.

**Resultado**: plan propio. Mismo costo que subir de tier el plan
compartido, pero sin arriesgar el App Service de producción.

### 5. Dominio administrado por Azure, sin DNS propio

**Alternativa**: dominio propio verificado (`alertas@tuempresa.com`), que
requiere registros TXT/SPF/DKIM/DMARC en el DNS de la empresa.

**Por qué no ahora**: no había dominio disponible ni acceso al DNS. El
dominio administrado (`*.azurecomm.net`) llega ya verificado, sin
configuración.

**Limitaciones asumidas**: el remitente queda fijo (`DoNotReply@<guid>...`),
tiene límites de envío pensados para dev/pruebas, y al ser un dominio nuevo
sin reputación es probable que los primeros correos caigan en spam.

Se puede migrar a dominio propio después **sin tocar el código**: solo
cambia `EmailSenderAddress`. Lo que sí es personalizable ya, sin DNS, es la
parte antes del `@`:

```bash
az communication email domain sender-username create \
  --domain-name AzureManagedDomain \
  --email-service-name <email-service> \
  --resource-group <rg> \
  --sender-username alertas --username alertas
```

### 6. Sin VNet

Hubo una variante con VNet (Private Endpoint sobre la cola + VNet
Integration en la Function). **Se descartó y se eliminó del repo.**

**Por qué**: obligaba a un plan de hosting más caro, y el beneficio era
parcial: **ACS Email no soporta Private Endpoint**, así que esa llamada
saldría por internet público de todos modos. El aislamiento habría sido a
medias, a cambio de bastante complejidad (subredes delegadas, Private DNS
Zones) y costo.

**Qué se usa en su lugar**: Managed Identity + RBAC. No hay claves ni
connection strings de Storage en ningún lado; el acceso se controla por
identidad, que para este caso da mejor relación seguridad/complejidad.

Si algún día se exige aislamiento de red, hay que: cerrar la Storage Account
(`--default-action Deny`), crear el Private Endpoint del subrecurso `queue`
con su Private DNS Zone, migrar la Function a un plan que soporte VNet
Integration, y **usar dos Storage Accounts separadas** (con una sola, cerrar
la cuenta rompe el despliegue).

### 7. `messageEncoding: "none"`

**El problema**: por defecto el host de Functions espera el contenido de la
cola en **Base64** (herencia del SDK antiguo de .NET), pero tanto
`az storage message put` como el SDK de Java escriben texto plano.

**El síntoma**: el mensaje se descartaba con *"Message decoding has failed"*,
se reintentaba 5 veces y terminaba en la cola de poison **sin invocar nunca
la Function**: sin excepción, sin request, sin un solo log de la aplicación.
Costó horas de diagnóstico porque todo lo demás (RBAC, red, despliegue,
binding) estaba bien.

**La solución**: `extensions.queues.messageEncoding = "none"` en `host.json`.
Y el sampling de Application Insights, activado por defecto, ocultaba justo
la traza que lo delataba — también se desactivó.

## Costos

| Recurso | Costo |
|---|---|
| App Service Plan B1 | Fijo mensual — el único relevante |
| Storage Account + colas | Céntimos con este volumen |
| Communication Services | Por correo enviado; el dominio administrado tiene cuota de pruebas |
| Application Insights | Gratis dentro de la cuota de ingesta mensual |

Para pausar el gasto sin borrar nada: detener la Function App o escalar el
plan a F1.
