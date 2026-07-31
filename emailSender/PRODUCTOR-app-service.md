# Guía para el productor (App Service de scoring)

Qué tiene que hacer la persona encargada del scoring para que sus eventos
lleguen a la Azure Function que envía los correos.

**Resumen: solo código.** La infraestructura y los permisos ya están
aprovisionados. No hay que crear ni conectar ningún servicio nuevo.

## Lo que ya está hecho (no tocar)

| Recurso | Valor |
|---|---|
| Storage Account | `stcolafraudecentinela` (region `chilecentral`) |
| Cola | `cola-casos-fraude` |
| Cola de poison | `cola-casos-fraude-poison` |
| RBAC del App Service | La Managed Identity de `appcentinelaprodgrupo3` ya tiene `Storage Queue Data Contributor` sobre esa cuenta |

Ojo: **no** es `sttransaccionesfase1ch1` (esa es la cuenta de evidencias
/ ingesta, otra cosa). La cola de fraude vive en `stcolafraudecentinela`.

Para verificar el RBAC:

```bash
az role assignment list \
  --assignee 6badd152-aa42-4ffd-a996-b7b766d700b1 \
  --scope /subscriptions/ee586a6c-da64-4247-9146-9ce13328e02f/resourceGroups/rg-centinela-prod/providers/Microsoft.Storage/storageAccounts/stcolafraudecentinela \
  -o table
```

## Dependencia (pom.xml)

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

## application.properties

```properties
# Cuenta donde vive la cola de casos de fraude (distinta de la de evidencias)
azure.storage.fraud-queue-account=stcolafraudecentinela
azure.storage.fraud-queue-name=cola-casos-fraude
```

## Configuración del cliente

Sin claves ni connection string: `DefaultAzureCredential` usa la Managed
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

## Publicar el evento

```java
@Service
public class FraudAlertPublisher {

    private final QueueClient fraudQueueClient;
    private final ObjectMapper objectMapper;

    public FraudAlertPublisher(QueueClient fraudQueueClient) {
        this.fraudQueueClient = fraudQueueClient;
        // JavaTimeModule + deshabilitar timestamps numericos: los Instant tienen
        // que salir como ISO-8601 ("2026-07-30T15:00:00Z"), que es lo que la
        // Function espera al deserializar.
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void publicar(FraudAlertEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            fraudQueueClient.sendMessage(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                "No se pudo serializar el FraudAlertEvent " + event.transactionId(), e);
        }
    }
}
```

## Detalles que importan (aprendidos a los golpes)

1. **Texto plano, no Base64.** `sendMessage(String)` del SDK de Java escribe
   texto plano, y la Function está configurada con
   `messageEncoding: "none"` para leerlo así. Si el productor lo codifica en
   Base64 a mano, la Function falla con
   `"Message decoding has failed"`, reintenta 5 veces y manda el mensaje a la
   cola de poison **sin generar ni un log de la aplicación** (fue lo que más
   costó diagnosticar). No codifiques nada, manda el JSON tal cual.

2. **Los nombres de los campos deben coincidir exactamente** con el record
   `FraudAlertEvent` de la Function (ver
   `email-sender-function/src/main/java/com/company/emailsender/dto/`).
   Cualquier campo extra hace fallar la deserialización.

3. **Las fechas van en ISO-8601.** Sin
   `disable(WRITE_DATES_AS_TIMESTAMPS)`, Jackson serializa los `Instant`
   como números y la Function no los va a poder leer.

4. **No hace falta crear la cola desde código.** Ya existe. Tampoco llames
   a `createQueue()`: la Managed Identity tiene permisos de datos
   (`Storage Queue Data Contributor`), no de gestión, así que fallaría.

## Formato del mensaje

Ejemplo real y validado (el mismo de
`email-sender-function/samples/fraud-alert-event-sample.json`):

```json
{
  "transactionId": "txn-test-001",
  "accountId": "acc-12345",
  "amount": 1500.00,
  "currency": "USD",
  "occurredAt": "2026-07-30T15:00:00Z",
  "ingestedAt": "2026-07-30T15:00:05Z",
  "latitude": 6.2442,
  "longitude": -75.5812,
  "merchantId": "merch-999",
  "merchantCategory": "gambling",
  "score": 85,
  "threshold": 60,
  "scoredAt": "2026-07-30T15:00:10Z",
  "activations": [
    { "ruleCode": "VELOCITY", "description": "3 transacciones en 3 minutos", "points": 35 },
    { "ruleCode": "MERCHANT_RISK", "description": "Categoria de alto riesgo: gambling", "points": 20 }
  ]
}
```

## Cómo probar sin desplegar

Encolar un mensaje a mano y ver si llega el correo:

```bash
export STORAGE_ACCOUNT=stcolafraudecentinela
bash email-sender-function/samples/enqueue_test_message.sh
```

Y revisar los logs de la Function:

```bash
export RG=rg-centinela-prod
export FUNCTION_APP_NAME=func-email-sender-centinela
bash email-sender-function/samples/query_logs.sh
```

Si un mensaje falla 5 veces, termina en `cola-casos-fraude-poison`:

```bash
az storage message peek \
  --queue-name cola-casos-fraude-poison \
  --account-name stcolafraudecentinela \
  --auth-mode login --num-messages 32
```
