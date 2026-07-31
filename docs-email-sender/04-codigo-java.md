# El código Java de la Function

Guía del proyecto `emailSender/email-sender-function`: qué hace cada clase,
cómo se conectan, y cómo personalizar el correo.

Java 21 + Maven, **sin Spring**: se usa el modelo nativo de Azure Functions
(`@FunctionName` + `@QueueTrigger`). El porqué está en
[`01-servicios-y-arquitectura.md`](01-servicios-y-arquitectura.md), decisión 2.

## Estructura

```
src/main/java/com/company/emailsender/
├── function/
│   └── EmailNotificationFunction.java     ← punto de entrada (Queue Trigger)
├── dto/
│   └── FraudAlertEvent.java               ← el mensaje que llega
├── model/
│   └── RuleActivation.java                ← regla de fraude activada
├── service/
│   ├── EmailService.java                  ← interfaz de envío
│   └── AzureCommunicationEmailService.java← implementación con ACS
├── template/
│   └── FraudEmailTemplateBuilder.java     ← construye asunto y HTML
└── config/
    └── AzureCommunicationConfig.java      ← crea y cachea el EmailClient
```

Flujo de una invocación:

```
Queue Trigger
   → EmailNotificationFunction.run()
       → deserializa el JSON a FraudAlertEvent
       → EmailService.send(event)
           → FraudEmailTemplateBuilder.buildSubject() / buildHtml()
           → EmailClient.beginSend()  → Azure Communication Services
```

## Las clases

### `EmailNotificationFunction`

Punto de entrada. El binding se declara con anotaciones:

```java
@FunctionName("EmailNotificationFunction")
public void run(
    @QueueTrigger(
        name = "message",
        queueName = "%FraudQueueName%",     // %...% = lee el App Setting
        connection = "FraudQueueStorage"    // prefijo de las settings de conexión
    ) String message,
    ExecutionContext context)
```

- `queueName = "%FraudQueueName%"`: los `%` hacen que Azure resuelva el valor
  desde los App Settings en vez de tomarlo literal. Así el nombre de la cola
  no está hardcodeado.
- `connection = "FraudQueueStorage"`: no es una connection string, es el
  **prefijo** de las settings `FraudQueueStorage__queueServiceUri` y
  `FraudQueueStorage__credential=managedidentity`. Es lo que permite
  autenticarse por Managed Identity sin claves.

Dos detalles no obvios en esta clase:

**El `EmailService` se construye de forma perezosa** (`emailService()`), no
en el constructor. Si se construyera en el constructor y fallara (una app
setting ausente, credencial inválida), la excepción ocurriría cuando el
worker de Java instancia la clase — *fuera* del `try/catch` — y el resultado
sería: invocación fallida, 5 reintentos, mensaje a la cola de poison, y
**cero logs**. Con la construcción perezosa, cualquier fallo queda dentro del
`try/catch` y se registra.

**Relanza la excepción** (`throw new RuntimeException(e)`) después de
loguearla. Es deliberado: así el host sabe que la invocación falló y aplica
la política de reintentos. Si se tragara la excepción, el mensaje se daría
por procesado y la alerta se perdería en silencio.

También hay un constructor package-private que recibe un `EmailService`, para
poder inyectar un mock en tests.

### `FraudAlertEvent` (record)

DTO plano con los datos de la alerta. Es el contrato con el productor:
cambiarlo obliga a coordinar con el equipo de scoring. Campos y formato en
[`02-formato-mensaje-cola.md`](02-formato-mensaje-cola.md).

Se deserializa con un `ObjectMapper` estático que registra `JavaTimeModule`
(necesario para los `Instant` en ISO-8601).

### `RuleActivation` (record)

Una regla de fraude que se disparó: `ruleCode`, `description`, `points`.
Alimenta la tabla de reglas del correo.

### `EmailService` (interfaz)

Un solo método: `void send(FraudAlertEvent event)`.

Existe para que `EmailNotificationFunction` no dependa de ACS directamente:
permite mockear en tests y cambiar de proveedor de correo tocando una sola
clase.

### `AzureCommunicationEmailService`

Implementación real. Construye el `EmailMessage` y lo envía.

```java
EmailMessage message = new EmailMessage()
    .setSenderAddress(senderAddress)
    .setSubject(subject)
    .setBodyHtml(html);
message.setToRecipients(/* destinatarios */);

SyncPoller<EmailSendResult, EmailSendResult> poller = emailClient.beginSend(message);
PollResponse<EmailSendResult> response = poller.waitForCompletion(SEND_TIMEOUT);
```

ACS es asíncrono: `beginSend` devuelve un poller y `waitForCompletion`
espera la confirmación (timeout de 2 minutos). Se espera a propósito, para
que un fallo de envío haga fallar la invocación y active los reintentos de
la cola.

Lee tres App Settings al construirse, y falla con un mensaje claro si falta
alguna:

| Setting | Uso |
|---|---|
| `EmailSenderAddress` | Remitente (`DoNotReply@....azurecomm.net`) |
| `FraudAlertRecipients` | Destinatarios, separados por coma |
| `CommunicationServicesConnectionString` | Credencial de ACS (vía `AzureCommunicationConfig`) |

### `FraudEmailTemplateBuilder`

Convierte el evento en asunto y HTML. Sin dependencias externas (ni
Thymeleaf ni similar): `StringBuilder` y HTML inline, suficiente para un
correo y sin sumar peso al cold start.

- `buildSubject(event)` → `"Alerta de fraude - Transaccion txn-001 (score 85/60)"`
- `buildHtml(event)` → título, tabla de datos y, si hay activaciones, tabla
  de reglas.

Escapa `&`, `<` y `>` en todos los valores (`escape()`), para que un dato de
la transacción no pueda inyectar HTML en el correo.

### `AzureCommunicationConfig`

Crea el `EmailClient` a partir del connection string y lo **cachea en un
campo estático** con doble verificación (`volatile` + `synchronized`).

El runtime de Functions reutiliza la instancia de la clase entre
invocaciones dentro del mismo host, así que el cliente se construye una sola
vez en vez de en cada mensaje (construirlo implica levantar el stack HTTP de
Netty, que no es barato).

## Personalizar el correo

### Cambiar el asunto

`FraudEmailTemplateBuilder.buildSubject()`:

```java
public String buildSubject(FraudAlertEvent event) {
    return String.format(
        "Alerta de fraude - Transaccion %s (score %d/%d)",
        event.transactionId(), event.score(), event.threshold());
}
```

Idea útil: marcar la severidad según cuánto se pasó del umbral.

```java
String prefijo = event.score() >= event.threshold() * 1.5 ? "[CRITICO]" : "[ALERTA]";
```

### Cambiar el cuerpo HTML

`buildHtml()`. Los estilos van **inline** a propósito: la mayoría de
clientes de correo ignoran o eliminan los `<style>` del `<head>`.

Para agregar una fila a la tabla de datos:

```java
appendRow(html, "Etiqueta", valor);   // ya escapa el valor
```

Si el HTML crece, conviene moverlo a un archivo en `src/main/resources` y
cargarlo como recurso, dejando `FraudEmailTemplateBuilder` solo con la
sustitución de valores.

### Cambiar destinatarios

No se toca código: es el App Setting `FraudAlertRecipients`, separado por
comas.

```bash
az functionapp config appsettings set \
  --name func-email-sender-centinela --resource-group rg-centinela-prod \
  --settings "FraudAlertRecipients=uno@dominio.com,dos@dominio.com"
```

O editando `local.settings.json` y corriendo `bash docs/scripts/05-app-settings.sh`.

### Destinatarios en copia (CC/BCC)

`EmailMessage` soporta `setCcRecipients` / `setBccRecipients`. Se añadirían
en `AzureCommunicationEmailService.send()`, leyendo nuevas App Settings con
el mismo patrón que `FraudAlertRecipients`.

### Enrutar por severidad

Como `send()` recibe el evento completo, se puede elegir destinatario según
el contenido — por ejemplo, sumar un supervisor cuando el score es muy alto.
La lógica va en `AzureCommunicationEmailService`; el resto no cambia.

### Cambiar de proveedor de correo

Implementar `EmailService` con otra clase y usarla en
`EmailNotificationFunction.emailService()`. `FraudEmailTemplateBuilder` se
reutiliza tal cual.

## Configuración fuera del código

`host.json`:

| Clave | Valor | Por qué |
|---|---|---|
| `extensions.queues.messageEncoding` | `"none"` | Los mensajes son texto plano, no Base64. Sin esto la Function nunca se invoca (ver decisión 7). |
| `extensions.queues.maxDequeueCount` | `5` | Reintentos antes de mandar a la cola de poison. |
| `extensions.queues.visibilityTimeout` | `00:00:30` | Espera entre reintentos. |
| `extensions.queues.batchSize` | `8` | Mensajes en paralelo por instancia. |
| `logging.applicationInsights.samplingSettings.isEnabled` | `false` | Con sampling se pierden trazas justo cuando más se necesitan. Volumen bajo, se puede permitir. |

## Compilar, probar y desplegar

```bash
cd emailSender/email-sender-function
mvn clean package
mvn azure-functions:deploy
```

Correr en local requiere [Azure Functions Core Tools v4](https://learn.microsoft.com/azure/azure-functions/functions-run-local)
y un `local.settings.json` con valores reales:

```bash
mvn azure-functions:run
```

Ver logs en Azure:

```bash
export RG=rg-centinela-prod
export FUNCTION_APP_NAME=func-email-sender-centinela
bash samples/query_logs.sh
```

## Sobre los tests

No hay tests todavía. Las dependencias (JUnit 5 y Mockito) ya están en el
`pom.xml`, y el diseño está preparado para ellos:

- `FraudEmailTemplateBuilder` es una clase pura sin dependencias: se prueba
  directamente.
- `EmailNotificationFunction` tiene un constructor package-private que
  acepta un `EmailService` mockeado, y `ExecutionContext` también es
  mockeable.
