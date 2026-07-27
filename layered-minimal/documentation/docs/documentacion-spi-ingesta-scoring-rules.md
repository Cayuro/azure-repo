# Proyecto Centinela — SPI de Ingesta y Motor de Scoring-Rules

> Módulo: `layered-minimal` (Spring Boot 3.3.13 / Java 21)
> Este documento cubre: (1) los comandos que un developer necesita para correr y probar el proyecto **sin leer nada más**, y (2) la explicación detallada, paso a paso, de qué hace cada pieza y por qué está diseñada así.

---

# PARTE 1 — QUICK START PARA DEVELOPERS (scripts y pruebas)

Esta sección es autocontenida: cópiala y pégala en tu terminal. No necesitas leer el resto del documento para levantar el proyecto y probarlo.

## 1.1 Requisitos previos

| Herramienta | Versión | Notas |
|---|---|---|
| Java (JDK) | 21 | El `pom.xml` fija `java.version=21` |
| Maven | Wrapper incluido (`mvnw` / `mvnw.cmd`) | No es obligatorio tener Maven instalado |
| Azure CLI | Última estable | Solo necesario si vas a probar la subida de evidencias contra un Storage Account real |
| Cuenta de Azure Storage | — | Solo necesaria para el endpoint de evidencias (blob storage) |

## 1.2 Configuración mínima (`application.properties`)

El repo **no trae** `application.properties` (no está versionado). Crea el archivo en:

```
layered-minimal/src/main/resources/application.properties
```

Con este contenido mínimo para desarrollo local:

```properties
# --- Azure Blob Storage (requerido por EvidenciaService) ---
azure.storage.account-name=<nombre-de-tu-storage-account>
azure.storage.container-name=evidencias-financieras-privado

# --- Reglas de scoring (todas tienen defaults en el codigo, aqui puedes sobreescribirlas) ---
ingesta.scoring.threshold=60
ingesta.scoring.velocity-window-minutes=3
ingesta.scoring.velocity-minimum-transactions=3
ingesta.scoring.velocity-points=35
ingesta.scoring.amount-multiplier=5.0
ingesta.scoring.amount-points=30
ingesta.scoring.geo-max-speed-kmh=1000.0
ingesta.scoring.geo-points=17
ingesta.scoring.merchant-points=20
ingesta.scoring.risk-merchant-categories=gambling,crypto,cash_advance
```

> ⚠️ Si no tienes una cuenta de Azure Storage a la mano, igual puedes correr y probar **ingesta + scoring** (no usan Storage). Solo el endpoint `POST /evidencias` la necesita. Autenticación sin llaves vía `DefaultAzureCredentialBuilder` (usa `az login`, Managed Identity o variables `AZURE_CLIENT_ID` / `AZURE_CLIENT_SECRET` / `AZURE_TENANT_ID`).

## 1.3 Autenticarte contra Azure (solo si vas a probar evidencias)

```bash
az login
az account set --subscription "<subscription-id>"
```

## 1.4 Compilar y correr tests

Desde la carpeta `layered-minimal/`:

```bash
# Windows
..\mvnw.cmd -f .\pom.xml clean test

# Linux/Mac
../mvnw -f ./pom.xml clean test
```

Salida esperada: `TransactionControllerIT` y `TransactionScoringIT` en verde (BUILD SUCCESS).

## 1.5 Levantar la aplicación

```bash
# Windows
..\mvnw.cmd -f .\pom.xml spring-boot:run

# Linux/Mac
../mvnw -f ./pom.xml spring-boot:run
```

La API queda disponible en `http://localhost:8080`.

## 1.6 Probar la SPI de ingesta (curl)

### 1.6.1 Ingestar una transacción nueva

```bash
curl -i -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "tx-1001",
    "accountId": "acc-77",
    "amount": 1250.50,
    "currency": "COP",
    "occurredAt": "2026-07-27T14:00:00Z",
    "latitude": 4.7110,
    "longitude": -74.0721,
    "merchantId": "m-990",
    "merchantCategory": "retail"
  }'
```

**Respuesta esperada:** `202 Accepted`

```json
{
  "transactionId": "tx-1001",
  "status": "RECIBIDA",
  "ingestedAt": "2026-07-27T14:00:01.123Z"
}
```

### 1.6.2 Reenviar la misma transacción (idempotencia)

```bash
curl -i -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "tx-1001",
    "accountId": "acc-77",
    "amount": 1250.50,
    "currency": "COP",
    "occurredAt": "2026-07-27T14:00:00Z",
    "latitude": 4.7110,
    "longitude": -74.0721,
    "merchantId": "m-990",
    "merchantCategory": "retail"
  }'
```

**Respuesta esperada:** `200 OK` con `"status": "YA_RECIBIDA"` y el **mismo** `ingestedAt` original (no se sobreescribe).

### 1.6.3 Consultar una transacción

```bash
curl -i http://localhost:8080/api/v1/transactions/tx-1001
```

**Respuesta esperada:** `200 OK` con el objeto `Transaction` completo. Si el id no existe: `404 Not Found`.

### 1.6.4 Subir una evidencia (multipart)

```bash
curl -i -X POST http://localhost:8080/api/v1/transactions/tx-1001/evidencias \
  -F "file=@/ruta/local/comprobante.pdf"
```

**Respuesta esperada:** `201 Created`

```json
{
  "transactionId": "tx-1001",
  "blobName": "tx_tx-1001_a1b2c3d4.pdf"
}
```

Campos de validación a tener en cuenta al testear:

| Caso de prueba | Resultado esperado |
|---|---|
| Archivo > 5 MB | `400 Bad Request` — "El archivo excede el limite permitido de 5 Megabytes." |
| Archivo con extensión `.pdf` pero contenido falso (no magic number `%PDF`) | `400 Bad Request` — "Tipo de archivo invalido..." |
| Archivo PNG real (magic number `\x89PNG`) | `201 Created` |
| `transactionId` inexistente | `404 Not Found` |

## 1.7 Probar el motor de scoring-rules (disparar un caso de fraude)

El scoring corre **de forma asíncrona** después de la ingesta (mira la Parte 2 para el detalle). Para forzar un score alto y abrir un `FraudCase`, envía 3 transacciones a la **misma cuenta**, en una ventana corta de tiempo, con un salto geográfico imposible y un comercio de riesgo:

```bash
# Transacción 1 (Bogotá)
curl -s -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "tx-s-1",
    "accountId": "acc-score-1",
    "amount": 100.00,
    "currency": "COP",
    "occurredAt": "2026-07-27T10:00:00Z",
    "latitude": 4.7110,
    "longitude": -74.0721,
    "merchantId": "m-100",
    "merchantCategory": "retail"
  }'

# Transacción 2 (Bogotá, 1 minuto después)
curl -s -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "tx-s-2",
    "accountId": "acc-score-1",
    "amount": 100.00,
    "currency": "COP",
    "occurredAt": "2026-07-27T10:01:00Z",
    "latitude": 4.7111,
    "longitude": -74.0722,
    "merchantId": "m-101",
    "merchantCategory": "retail"
  }'

# Transacción 3 (Madrid, 1 minuto después, monto atípico, comercio de riesgo)
curl -s -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "tx-s-3",
    "accountId": "acc-score-1",
    "amount": 1000.00,
    "currency": "COP",
    "occurredAt": "2026-07-27T10:02:00Z",
    "latitude": 40.4168,
    "longitude": -3.7038,
    "merchantId": "m-102",
    "merchantCategory": "gambling"
  }'
```

Con esto se activan las **4 reglas** a la vez (velocidad, monto atípico, salto geográfico imposible y comercio de riesgo), el score supera el `threshold` de 60 puntos y se abre un `FraudCase` con estado `ABIERTO`.

> No hay endpoint HTTP expuesto para consultar `TransactionScore` ni `FraudCase` directamente en este módulo (se validan hoy vía tests de integración, `TransactionScoreRepository` y `FraudCaseRepository`). Ver Parte 2, sección 2.6, si vas a agregar ese endpoint.

## 1.8 Comandos resumen (cheatsheet)

```bash
# Build + test
../mvnw -f ./pom.xml clean test

# Run
../mvnw -f ./pom.xml spring-boot:run

# Ingestar
curl -X POST localhost:8080/api/v1/transactions -H "Content-Type: application/json" -d @payload.json

# Consultar
curl localhost:8080/api/v1/transactions/{transactionId}

# Subir evidencia
curl -X POST localhost:8080/api/v1/transactions/{transactionId}/evidencias -F "file=@archivo.pdf"

# Solo el test de scoring (JUnit, por nombre de clase)
../mvnw -f ./pom.xml test -Dtest=TransactionScoringIT

# Solo el test del controller
../mvnw -f ./pom.xml test -Dtest=TransactionControllerIT

# Empaquetar el JAR ejecutable (lo mismo que hace el pipeline de GitHub Actions)
../mvnw -f ./pom.xml clean package -DskipTests
```

---

# PARTE 2 — EXPLICACIÓN DETALLADA (paso a paso, y por qué)

## 2.1 Visión general de la arquitectura

El módulo `layered-minimal` implementa una **arquitectura en capas (layered architecture)** clásica, deliberadamente simple para que cualquier persona del equipo pueda entenderla sin curva de aprendizaje adicional:

```
controller/     -> Capa HTTP: recibe requests, valida forma, delega a service, traduce a HTTP status
service/        -> Lógica de negocio: ingesta, scoring, evidencias
repository/     -> Persistencia (hoy en memoria, con contrato/interfaz listo para cambiar a BD real)
dto/            -> Contratos de entrada/salida de la API (nunca se expone el modelo interno directamente)
model/          -> Entidades de dominio (Transaction, TransactionScore, RuleActivation, FraudCase)
exception/      -> Excepciones de negocio propias
handler/        -> Traductor centralizado de excepciones -> respuestas HTTP (RFC de error consistente)
messaging/      -> Publicación de eventos internos (desacopla ingesta de scoring)
config/         -> Configuración de Spring (scoring properties, executor async, reloj)
```

**Por qué así:** separar `dto` de `model` evita que un cambio interno (por ejemplo, agregar un campo de auditoría al `Transaction`) rompa el contrato público de la API. Separar `repository` detrás de una interfaz (`TransactionRepository`, `TransactionScoreRepository`, `FraudCaseRepository`) permite reemplazar el almacenamiento en memoria por una base de datos real (Azure SQL, Cosmos DB, etc.) sin tocar la lógica de negocio ni los controllers.

## 2.2 La SPI de Ingesta — flujo completo

"SPI de ingesta" se refiere al conjunto de endpoints y componentes bajo `/api/v1/transactions`, cuya responsabilidad es **recibir transacciones financieras, validarlas, evitar duplicados y disparar el proceso de scoring sin bloquear al cliente que las envía.**

### 2.2.1 Entrada: `TransactionController`

`TransactionController` expone 3 endpoints:

| Método | Ruta | Propósito |
|---|---|---|
| `POST` | `/api/v1/transactions` | Ingestar una transacción nueva |
| `GET` | `/api/v1/transactions/{transactionId}` | Consultar una transacción por id |
| `POST` | `/api/v1/transactions/{transactionId}/evidencias` | Adjuntar evidencia (archivo) a una transacción existente |

El controller **no contiene lógica de negocio**. Su única responsabilidad es:
1. Deserializar y validar la forma del JSON de entrada (`@Valid TransactionRequest`).
2. Delegar al `TransactionService`.
3. Traducir el resultado del service a un código HTTP correcto.

Por ejemplo, en `receive(...)`:

```java
TransactionResponse response = service.ingest(request);
if ("YA_RECIBIDA".equals(response.status())) {
    return ResponseEntity.ok(response);              // 200 OK
}
return ResponseEntity.status(HttpStatus.ACCEPTED).body(response); // 202 Accepted
```

**Por qué 202 y no 201:** la transacción se acepta para procesamiento asíncrono (el scoring todavía no ha corrido cuando se responde), por eso es semánticamente correcto usar `202 Accepted` (petición aceptada, procesamiento en curso) y no `201 Created` (que implicaría un recurso completamente materializado, incluyendo su score).

### 2.2.2 El contrato de entrada: `TransactionRequest`

```java
public record TransactionRequest(
        @NotBlank String transactionId,
        @NotBlank String accountId,
        @NotNull @DecimalMin("0.01") @DecimalMax("999999999999.99") BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull Instant occurredAt,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @NotBlank String merchantId,
        @NotBlank String merchantCategory
) {}
```

**Por qué es un `record` y no una clase con getters/setters:** los `record` de Java son inmutables por naturaleza — una vez creado un `TransactionRequest`, sus valores no cambian. Esto es intencional en un dominio financiero: una transacción, una vez ingestada, no debe poder mutarse accidentalmente en memoria mientras viaja por las capas.

**Por qué las validaciones están en anotaciones (Bean Validation) y no en código:** permite que Spring rechace peticiones mal formadas **antes** de que lleguen a la lógica de negocio, de forma declarativa y centralizada. `BigDecimal` se usa para `amount` (no `double`) porque en dinero **nunca se debe usar coma flotante binaria** (evita errores de redondeo).

### 2.2.3 Lógica de negocio: `TransactionService.ingest(...)`

Paso a paso, esto es lo que ocurre cuando llega un `POST /transactions`:

1. **Validación semántica adicional** (`validate(request)`): más allá de lo que valida Bean Validation, se comprueba que `occurredAt` no sea una fecha futura (una transacción no puede "ocurrir" en el futuro). Si falla, se lanza `InvalidTransactionException` con la lista de errores.
2. **Construcción del modelo de dominio** `Transaction`, agregando `ingestedAt = Instant.now(clock)` — el momento real en que el sistema la procesó (distinto de `occurredAt`, que es el momento declarado por el origen de la transacción).
3. **Persistencia idempotente**: `repository.saveIfAbsent(transaction)`. Esto es clave — ver 2.2.4.
4. Si la transacción **es nueva**: se publica un evento (`eventPublisher.publish(transaction)`) y se responde `RECIBIDA`.
5. Si la transacción **ya existía** (mismo `transactionId`): se recupera la transacción original y se responde `YA_RECIBIDA` con el `ingestedAt` **original**, sin volver a publicar el evento (evita reprocesar scoring dos veces para la misma transacción).

### 2.2.4 Idempotencia: por qué importa tanto en ingesta financiera

`TransactionRepository.saveIfAbsent(...)` usa `ConcurrentHashMap.putIfAbsent(...)` internamente (`InMemoryTransactionRepository`), que es una operación **atómica**: bajo concurrencia (dos requests llegando casi al mismo tiempo con el mismo `transactionId`), solo una gana y la otra recibe `ALREADY_EXISTS`. Esto es crítico porque:

- Los sistemas de origen (switches de pago, pasarelas) suelen **reintentar** envíos ante timeouts de red, sin garantía de que el primer envío no haya llegado.
- Sin idempotencia, una transacción se podría **contar dos veces** en el scoring (afectando falsamente reglas de velocidad y monto), o abrir dos `FraudCase` para el mismo hecho.

### 2.2.5 Desacoplar ingesta de scoring: eventos + ejecutor asíncrono

En lugar de que `TransactionService` llame directamente al motor de scoring, publica un evento de dominio:

```java
public record TransactionIngestedEvent(Transaction transaction) {}
```

`SpringTransactionEventPublisher` implementa la interfaz `TransactionEventPublisher` (permite cambiar el mecanismo de publicación — hoy eventos internos de Spring, mañana podría ser una cola de Azure Storage/Service Bus — sin tocar `TransactionService`).

`TransactionScoringService.onTransactionIngested(...)` está anotado con `@EventListener` y es quien realmente ejecuta el scoring.

**Por qué es asíncrono:** `AsyncEventConfig` reemplaza el multicaster por defecto de Spring por uno que ejecuta los listeners en un `TaskExecutor` separado (`SimpleAsyncTaskExecutor`). Esto significa que:

- El cliente que envía el `POST /transactions` recibe su `202 Accepted` **inmediatamente**, sin esperar a que corran las 4 reglas de scoring.
- El scoring (que puede volverse más costoso con el tiempo — más reglas, más historial a recorrer) no afecta la latencia percibida de la API de ingesta.
- Si el scoring falla, no debería tumbar la respuesta de ingesta (aislamiento de fallos) — aunque hoy no hay manejo explícito de reintentos/dead-letter, ver "Mejoras propuestas" en 2.7.

### 2.2.6 Manejo de errores: `GlobalExceptionHandler`

Todos los controllers comparten un único traductor de excepciones (`@RestControllerAdvice`), que centraliza la construcción de `ApiErrorResponse` para que **toda** la API tenga el mismo formato de error, sin importar qué excepción ocurrió:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "La transaccion no cumple el contrato",
  "details": ["amount: must be greater than or equal to 0.01"]
}
```

Casos cubiertos: validación de Bean Validation (`MethodArgumentNotValidException`, `ConstraintViolationException`), reglas de negocio propias (`InvalidTransactionException`), JSON mal formado o con campos no reconocidos (`HttpMessageNotReadableException` / `UnrecognizedPropertyException`), recurso no encontrado (`TransactionNotFoundException` → 404), y un catch-all para errores no anticipados (500, sin filtrar detalles internos al cliente — buena práctica de seguridad).

## 2.3 El motor de Scoring-Rules — explicación regla por regla

`TransactionScoringEngine.score(transaction, history)` es el corazón analítico del sistema. Recibe la transacción entrante y **el historial de transacciones previas de la misma cuenta** (`history`, obtenido por `TransactionScoringService` vía `transactionRepository.findByAccountId(...)`, excluyendo la transacción actual).

Evalúa **4 reglas independientes**, cada una suma puntos si se activa, y el score final es la suma de todas las activaciones. Si el score total supera el `threshold` configurado (default 60), se abre un `FraudCase`.

Toda la parametría vive en `ScoringProperties` (prefijo `ingesta.scoring.*`), **nunca hardcodeada** — así el equipo de negocio puede ajustar sensibilidad sin tocar código ni recompilar.

### 2.3.1 Regla `VELOCIDAD` (velocidad de transacciones)

**Qué detecta:** demasiadas transacciones de la misma cuenta en muy poco tiempo (patrón típico de tarjeta comprometida usada rápidamente en múltiples comercios).

**Cómo funciona:**
```java
Instant windowStart = transaction.ingestedAt().minus(Duration.ofMinutes(velocityWindowMinutes));
long recentTransactions = history.stream()
        .filter(item -> !item.ingestedAt().isBefore(windowStart))
        .count() + 1; // +1 por la transacción actual
if (recentTransactions >= velocityMinimumTransactions) { activar(); }
```
- `velocityWindowMinutes` (default 3): ventana de tiempo hacia atrás a considerar.
- `velocityMinimumTransactions` (default 3): cuántas transacciones dentro de esa ventana disparan la regla.
- `velocityPoints` (default 35): puntos que suma.

**Por qué se usa `ingestedAt` y no `occurredAt`:** `ingestedAt` es el reloj del sistema (confiable, controlado por `Clock`), mientras que `occurredAt` es declarado por el origen y podría venir manipulado o desordenado. Para detectar patrones de *velocidad real de llegada al sistema*, `ingestedAt` es la referencia correcta.

### 2.3.2 Regla `MONTO_ATIPICO` (monto atípico)

**Qué detecta:** un monto muy por encima del comportamiento histórico de la cuenta (posible transacción fraudulenta de alto valor).

**Cómo funciona:**
```java
BigDecimal average = promedio(history.map(Transaction::amount));
BigDecimal thresholdAmount = average.multiply(amountMultiplier);
if (transaction.amount() > thresholdAmount) { activar(); }
```
- Si la cuenta no tiene historial (`history.isEmpty()`), la regla **no se evalúa** (no hay línea base para comparar; evita falsos positivos en la primera transacción de una cuenta nueva).
- `amountMultiplier` (default 5.0): la transacción debe superar 5 veces el promedio histórico para activarse.
- `amountPoints` (default 30).

**Por qué `BigDecimal` con `RoundingMode.HALF_UP` y escala 2:** consistente con el manejo de dinero en toda la aplicación — nunca se opera con `double` para montos.

### 2.3.3 Regla `GEO_IMPOSIBLE` (viaje geográficamente imposible)

**Qué detecta:** dos transacciones de la misma cuenta ocurriendo en ubicaciones cuya distancia, dividida por el tiempo transcurrido, implicaría una velocidad de desplazamiento físicamente imposible (por ejemplo, Bogotá → Madrid en 2 minutos).

**Cómo funciona:**
1. Toma la transacción histórica más reciente (`max` por `ingestedAt`).
2. Calcula la distancia en kilómetros entre ambas coordenadas usando la **fórmula del haversine** (distancia de gran círculo sobre la esfera terrestre, radio 6371 km).
3. Calcula la velocidad implícita: `distanciaKm / horasTranscurridas`.
4. Si supera `geoMaxSpeedKmH` (default 1000 km/h, más rápido que un vuelo comercial promedio), activa la regla.

```java
double speedKmH = distanceKm / (elapsedSeconds / 3600.0);
if (speedKmH > properties.getGeoMaxSpeedKmH()) { activar(); }
```

**Detalle de robustez:** si `elapsedSeconds <= 0` (transacciones con el mismo timestamp o desordenadas), se fuerza a `1` segundo para evitar división por cero — en ese caso, cualquier distancia > 0 dispara automáticamente la regla (lo cual es correcto: dos ubicaciones distintas en el mismo instante *es* imposible).

**Por qué 1000 km/h y no la velocidad de un avión comercial (~900 km/h):** es un margen de tolerancia configurable pensado para absorber pequeñas imprecisiones de GPS/reloj sin generar demasiados falsos positivos; se ajusta vía `ingesta.scoring.geo-max-speed-kmh` según el apetito de riesgo del negocio.

### 2.3.4 Regla `COMERCIO_RIESGO` (categoría de comercio de riesgo)

**Qué detecta:** transacciones en categorías de comercio históricamente asociadas a fraude o lavado (ej. `gambling`, `crypto`, `cash_advance`).

**Cómo funciona:** compara `merchantCategory` (case-insensitive) contra la lista configurable `riskMerchantCategories`. Si coincide, suma `merchantPoints` (default 20).

**Por qué es una lista configurable y no un enum fijo en código:** el catálogo de categorías de riesgo cambia con el tiempo (nuevas modalidades de fraude, nuevas regulaciones) — mantenerlo en configuración permite ajustarlo sin desplegar código nuevo.

### 2.3.5 Agregación del score y apertura del caso

```java
int score = activations.stream().mapToInt(RuleActivation::points).sum();
TransactionScore result = new TransactionScore(transactionId, score, threshold, scoredAt, activations);
```

Cada `RuleActivation` guarda no solo los puntos, sino **evidencia explicativa** (`details`, ej. `"speedKmH=1452.30"`, `"historicalAverage=100.00"`) — esto es clave para que un analista de fraude, al revisar un `FraudCase`, entienda **por qué** el sistema marcó esa transacción, sin tener que adivinar ni reconstruir el cálculo manualmente (explicabilidad del modelo de reglas).

`TransactionScoringService` decide abrir el caso:

```java
if (score.score() > score.threshold()) {
    FraudCase fraudCase = new FraudCase(UUID.randomUUID(), transactionId, score, "ABIERTO", now, activations);
    fraudCaseRepository.save(fraudCase);
}
```

**Por qué `>` y no `>=`:** el `threshold` representa el punto exacto de tolerancia configurada por negocio; superar estrictamente ese punto es lo que constituye una alerta accionable (el diseño es intencional y ajustable vía `ingesta.scoring.threshold`).

## 2.4 Evidencias y Azure Blob Storage

`EvidenciaService.cargarEvidenciaSegura(...)` gestiona la subida de comprobantes (facturas, capturas) asociados a una transacción, con 3 controles de seguridad explícitos en el código:

1. **Límite de tamaño (5 MB)**: mitiga ataques de denegación de servicio por archivos gigantes.
2. **Validación por *magic numbers*, no por extensión declarada**: se leen los primeros 4 bytes del archivo y se comparan contra las firmas reales de PDF (`%PDF`) y PNG (`\x89PNG`). Esto evita que un atacante suba un ejecutable renombrado como `.pdf`.
3. **Nombre de archivo generado en el servidor**: el nombre original del cliente se descarta por completo; se genera `tx_{transactionId}_{uuid8}.{ext}`. Esto elimina por diseño los ataques de *path traversal* (`../../etc/passwd`) y evita sobreescritura de archivos existentes.

**Autenticación sin llaves (RBAC / Managed Identity):**
```java
new BlobServiceClientBuilder()
    .endpoint("https://" + accountName + ".blob.core.windows.net")
    .credential(new DefaultAzureCredentialBuilder().build())
    .buildClient()
```
`DefaultAzureCredentialBuilder` prueba, en orden, múltiples mecanismos de autenticación (variables de entorno, Managed Identity, `az login` local, etc.) sin que el código ni la configuración contengan nunca una connection string o clave de acceso — alineado con el principio de "sin claves" que también se ve en los scripts de infraestructura (`--allow-shared-key-access false`).

## 2.5 WebApp / infraestructura (contexto de despliegue)

Aunque este documento se centra en el código de la SPI, es importante entender **dónde vive** en Azure, porque explica varias decisiones del código (por ejemplo, por qué la autenticación de Storage es sin llaves):

- La aplicación se despliega como **Azure App Service (Linux, runtime `JAVA|21-java21`)**, ver `infra/provision_app_service.sh`.
- El App Service tiene **Identidad Administrada (System-Assigned Managed Identity)** activada — es esta identidad la que `DefaultAzureCredentialBuilder` termina usando en producción, sin ninguna clave almacenada.
- El App Service está integrado a una **VNet** (`vnet-centinela-prod-v3`, subred `snet-app-prod`), y el **Storage Account** solo permite tráfico desde esa subred (`Default Action = Deny` + Service Endpoint), ver `documentation/docs-network/`.
- El pipeline de CI/CD (`.github/workflows/deploy.yml`) compila el JAR con Maven y lo despliega al App Service `appcentinelaprodgrupo3` en cada push a `develop`.

Para el detalle completo de cómo desplegar esta infraestructura con Terraform/Bicep (en vez de los scripts `az cli`), ver el archivo **`iac-terraform-bicep-despliegue.md`** que acompaña este documento.

## 2.6 Cobertura de pruebas actual

| Test | Qué valida |
|---|---|
| `TransactionControllerIT` | Ingesta exitosa + consulta (`postThenGetShouldWork`), e idempotencia real de punta a punta contra la API HTTP (`duplicateTransactionShouldKeepOriginalIngestedAt`) |
| `TransactionScoringIT` | Que 3 transacciones armadas para disparar las reglas terminen generando un score > threshold **y** un `FraudCase` abierto, esperando (con *polling* de hasta 3s) a que el procesamiento asíncrono termine |

Nota de diseño en los tests: como el scoring corre en un hilo separado, `TransactionScoringIT` no puede simplemente hacer el `POST` y verificar inmediatamente — usa un `awaitScore(...)` / `awaitFraudCase(...)` con polling, patrón correcto para probar sistemas eventualmente consistentes.

## 2.7 Mejoras propuestas (para roadmap, no implementadas hoy)

- Endpoint HTTP de solo lectura para consultar `TransactionScore` y `FraudCase` por `transactionId` (hoy solo accesibles vía repositorio/tests).
- Manejo de fallos en el listener asíncrono de scoring (hoy si `onTransactionIngested` lanza una excepción, no hay reintento ni dead-letter explícito).
- Migrar `InMemory*Repository` a una base de datos persistente (Azure SQL / Cosmos DB) — la interfaz ya está lista para ese cambio sin tocar services ni controllers.
- Exponer las reglas activas y sus puntajes en tiempo real como métricas (Application Insights) para monitoreo de negocio.
