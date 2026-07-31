# Guía de integración para el App Service de scoring

Dirigida a quien mantiene el App Service que calcula el score. Explica qué
hay que hacer para que sus eventos lleguen a la Function que envía los
correos.

**Resumen: solo código.** La infraestructura y los permisos ya están
aprovisionados. No hay que crear ni conectar ningún servicio nuevo, ni pedir
claves a nadie.

## Lo que ya está listo

| Recurso | Valor |
|---|---|
| Storage Account | `stcolafraudecentinela` (región `chilecentral`) |
| Cola | `cola-casos-fraude` |
| Cola de poison | `cola-casos-fraude-poison` |
| Permisos | La Managed Identity del App Service ya tiene `Storage Queue Data Contributor` sobre esa cuenta |

**Ojo con la cuenta**: no es `sttransaccionesfase1ch1` (esa es la de
evidencias e ingesta). La cola de fraude vive en `stcolafraudecentinela`.

Verificar el permiso:

```bash
az role assignment list \
  --assignee $(az webapp identity show --name appcentinelaprodgrupo3 -g rg-centinela-prod --query principalId -o tsv) \
  --scope $(az storage account show --name stcolafraudecentinela -g rg-centinela-prod --query id -o tsv) \
  -o table
```

## 1. Dependencias

```xml
<dependency>
    <groupId>com.azure</groupId>
    <artifactId>azure-storage-queue</artifactId>
    <version>12.25.1</version>
</dependency>
<dependency>
    <groupId>com.azure</groupId>
    <artifactId>azure-identity</artifactId>
    <version>1.13.3</version>
</dependency>
```

## 2. Configuración

```properties
# Cuenta donde vive la cola de casos de fraude
# (distinta de la de evidencias: azure.storage.account-name)
azure.storage.fraud-queue-account=stcolafraudecentinela
azure.storage.fraud-queue-name=cola-casos-fraude
```

## 3. El cliente de la cola

Sin claves ni connection strings: `DefaultAzureCredential` usa la Managed
Identity cuando corre en Azure, y tu sesión de `az login` cuando corres en
local.

```java
@Configuration
public class FraudQueueConfig {

    @Bean
    public QueueClient fraudQueueClient(
            @Value("${azure.storage.fraud-queue-account}") String accountName,
            @Value("${azure.storage.fraud-queue-name}") String queueName) {

        return new QueueClientBuilder()
            .endpoint("https://" + accountName + ".queue.core.windows.net")
            .queueName(queueName)
            .credential(new DefaultAzureCredentialBuilder().build())
            .buildClient();
    }
}
```

## 4. Publicar el evento

```java
@Service
public class FraudAlertPublisher {

    private static final Logger log = LoggerFactory.getLogger(FraudAlertPublisher.class);

    private final QueueClient fraudQueueClient;
    private final ObjectMapper objectMapper;

    public FraudAlertPublisher(QueueClient fraudQueueClient) {
        this.fraudQueueClient = fraudQueueClient;
        // Los Instant tienen que salir como ISO-8601 ("2026-07-30T15:00:00Z"),
        // que es lo que la Function espera al deserializar.
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void publicar(FraudAlertEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            fraudQueueClient.sendMessage(json);
            log.info("Alerta de fraude encolada para transaccion {}", event.transactionId());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                "No se pudo serializar el FraudAlertEvent " + event.transactionId(), e);
        }
    }
}
```

## 5. Llamarlo desde el motor de scoring

```java
if (score >= threshold) {
    fraudAlertPublisher.publicar(new FraudAlertEvent(
        transaccion.id(), transaccion.cuentaId(), transaccion.monto(),
        transaccion.moneda(), transaccion.ocurridaEn(), transaccion.ingestadaEn(),
        transaccion.latitud(), transaccion.longitud(),
        transaccion.comercioId(), transaccion.categoriaComercio(),
        score, threshold, Instant.now(), activaciones));
}
```

Decisión de diseño a tener en cuenta: si el encolado falla, ¿debe fallar
también la transacción de scoring? Lo habitual es que **no** — se registra el
error y se sigue, porque no notificar es menos grave que romper el flujo
principal. Pero es una decisión del equipo de scoring.

## Detalles que importan

Estos tres son los que fallan en la práctica (los dos primeros nos costaron
horas de diagnóstico):

**1. Texto plano, nunca Base64.** `sendMessage(String)` escribe texto plano y
la Function está configurada para leerlo así (`messageEncoding: "none"`). Si
codificas en Base64 a mano, el host falla con *"Message decoding has
failed"*, reintenta 5 veces y manda el mensaje a la cola de poison **sin
generar ni un log de la aplicación**. Manda el JSON tal cual.

**2. Fechas ISO-8601.** Sin `disable(WRITE_DATES_AS_TIMESTAMPS)`, Jackson
serializa los `Instant` como números y la Function no los puede leer.

**3. Nombres de campos exactos.** Deben coincidir uno a uno con el record
`FraudAlertEvent` de la Function; un campo de más hace fallar la
deserialización completa. Ver [`02-formato-mensaje-cola.md`](02-formato-mensaje-cola.md)
para la lista de campos.

**4. No llames a `createQueue()`.** La cola ya existe, y la Managed Identity
tiene permisos de *datos* (`Storage Queue Data Contributor`), no de gestión,
así que esa llamada fallaría.

## Probar la integración

Sin desplegar nada, encolando el mensaje de ejemplo:

```bash
export STORAGE_ACCOUNT=stcolafraudecentinela
bash emailSender/email-sender-function/samples/enqueue_test_message.sh
```

Ver qué hizo la Function con él:

```bash
export RG=rg-centinela-prod
export FUNCTION_APP_NAME=func-email-sender-centinela
bash emailSender/email-sender-function/samples/query_logs.sh
```

En un caso exitoso verás:

```
EmailNotificationFunction invocada, deserializando mensaje...
Procesando alerta de fraude para transaccion txn-test-001
Correo de alerta de fraude enviado. transactionId=txn-test-001 operationId=... status=...
```

Si algo falla, el mensaje acaba en la cola de poison:

```bash
az storage message peek \
  --queue-name cola-casos-fraude-poison \
  --account-name stcolafraudecentinela \
  --auth-mode login --num-messages 32
```

Ver el contenido exacto del mensaje que falló suele bastar para identificar
el problema.
