# Formato del mensaje de la cola

Contrato entre el App Service de scoring (productor) y la Azure Function
(consumidor). Cambiar este formato rompe la Function, así que cualquier
modificación hay que coordinarla entre ambos lados.

- **Cola**: `cola-casos-fraude` en `stcolafraudecentinela`
- **Formato**: JSON, **texto plano UTF-8** (no Base64)
- **Cola de fallos**: `cola-casos-fraude-poison`

## Ejemplo completo

Este es el mensaje de referencia, validado end-to-end. También está en
`emailSender/email-sender-function/samples/fraud-alert-event-sample.json`,
que es el que usa el script de prueba.

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

## Campos

Corresponden uno a uno con el record `FraudAlertEvent`
(`com.company.emailsender.dto.FraudAlertEvent`).

| Campo | Tipo Java | Formato JSON | Notas |
|---|---|---|---|
| `transactionId` | `String` | string | Identifica la transacción; aparece en el asunto del correo. |
| `accountId` | `String` | string | Cuenta afectada. |
| `amount` | `BigDecimal` | número | Sin comillas. Se usa `BigDecimal` para no perder precisión en importes. |
| `currency` | `String` | string | Ej. `"USD"`. Se muestra junto al importe. |
| `occurredAt` | `Instant` | ISO-8601 UTC | Cuándo ocurrió la transacción. |
| `ingestedAt` | `Instant` | ISO-8601 UTC | Cuándo la recibió el sistema. |
| `latitude` | `Double` | número o `null` | Opcional: si falta, no se muestra la ubicación. |
| `longitude` | `Double` | número o `null` | Opcional, igual que el anterior. |
| `merchantId` | `String` | string | Comercio. |
| `merchantCategory` | `String` | string | Categoría (`gambling`, `crypto`...). |
| `score` | `int` | número | Puntuación calculada. |
| `threshold` | `int` | número | Umbral superado. Ambos salen en el asunto. |
| `scoredAt` | `Instant` | ISO-8601 UTC | Cuándo se calculó el score. |
| `activations` | `List<RuleActivation>` | array | Reglas que se dispararon. Puede ir vacío o `null`: entonces se omite esa tabla del correo. |

### `RuleActivation`

| Campo | Tipo | Notas |
|---|---|---|
| `ruleCode` | `String` | Código corto, ej. `VELOCITY`. |
| `description` | `String` | Texto legible que verá el analista. |
| `points` | `int` | Puntos que aportó la regla. |

## Reglas que hay que respetar

Estas tres cosas son las que fallan en la práctica:

**1. Texto plano, nunca Base64.** El SDK de Java (`sendMessage(String)`)
escribe texto plano, y la Function está configurada con
`messageEncoding: "none"` para leerlo así. Si el productor codifica en
Base64 a mano, el host falla con *"Message decoding has failed"*, reintenta 5
veces y manda el mensaje a poison **sin generar ni un log de la aplicación**
— es un fallo silencioso y muy difícil de diagnosticar.

**2. Fechas en ISO-8601, no timestamps numéricos.** Hay que configurar
Jackson así:

```java
new ObjectMapper()
    .registerModule(new JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
```

Sin `disable(...)`, los `Instant` salen como números y la Function no los
puede leer.

**3. Nombres de campos exactos, sin campos de más.** La Function
deserializa con la configuración por defecto de Jackson: un campo
desconocido hace fallar todo el mensaje.

Si el productor necesita mandar campos extra, hay que agregarlos primero al
record `FraudAlertEvent` de la Function (o configurar
`FAIL_ON_UNKNOWN_PROPERTIES = false` en su `ObjectMapper`).

## Cómo verificar un mensaje

Encolar el de ejemplo y ver si llega el correo:

```bash
export STORAGE_ACCOUNT=stcolafraudecentinela
bash emailSender/email-sender-function/samples/enqueue_test_message.sh
```

Revisar cómo lo procesó la Function:

```bash
export RG=rg-centinela-prod
export FUNCTION_APP_NAME=func-email-sender-centinela
bash emailSender/email-sender-function/samples/query_logs.sh
```

Si algo falló, el mensaje termina aquí:

```bash
az storage message peek \
  --queue-name cola-casos-fraude-poison \
  --account-name stcolafraudecentinela \
  --auth-mode login --num-messages 32
```

Ver el contenido exacto del mensaje que falló suele bastar para encontrar el
problema (JSON mal formado, fecha numérica, campo de más).
