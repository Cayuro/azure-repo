# Centinela — SPI de Ingesta y Motor de Scoring-Rules (estado actualizado)

> **Nota de alcance de este documento.** Está construido a partir de las fuentes que definen el estado *vigente* del proyecto: `Claude.md` (arquitectura oficial del repo de ingesta), `Centinela-spec.md` y `semana2-spec.md` (alcance completo del sistema), `error.md` (registro de la migración a Java 11), `README.md`, `APP_SERVICE_DEPLOYMENT.md` y `app_service_help.md` (despliegue). No repite el detalle de una versión anterior del código que ya quedó obsoleta con esta migración — donde una fuente contradice a otra, se marca explícitamente en vez de inventar una respuesta única.
>
> **Runtime real de este repo hoy:** Java 11 + Spring Boot 2.7.18 (bajado desde Java 21 / Spring Boot 3.3, ver Parte 3). **Esto NO es lo mismo que documentación previa haya podido describir con Java 21 / Spring Boot 3.3 — si tienes ese material, ya no aplica al estado actual del runtime.**

---

# PARTE 1 — QUICK START PARA DEVELOPERS

Todo lo que necesitas para correr y probar el proyecto **sin leer el resto del documento**.

## 1.1 Requisitos previos

| Herramienta | Versión requerida | Fuente |
|---|---|---|
| JDK | **11** (no 17, no 21) | `error.md`, `APP_SERVICE_DEPLOYMENT.md` |
| Maven / wrapper | `mvnw` / `mvnw.cmd` incluido en el repo | `README.md` |
| Spring Boot | 2.7.18 (fijado en `pom.xml`) | `error.md` |
| Azure CLI | Última estable | Solo si vas a probar contra Storage real |

> ⚠️ Verifica tu `JAVA_HOME` antes de compilar. Si tu máquina tiene Java 17/21 como default, el build puede fallar o comportarse distinto a producción, que corre estrictamente en Java 11 (el Stack setting del App Service, no un archivo del repo — ver Parte 4).

## 1.2 Variables de entorno — ⚠️ hay una discrepancia entre fuentes, confírmala con el equipo

Las fuentes disponibles usan **nombres distintos** para la misma configuración de Storage. Antes de correr en perfil `azure`, confirma cuál es la que realmente lee el código:

| Fuente | Variables que menciona |
|---|---|
| `Claude.md` (sección 8, perfil `azure`) | `CENTINELA_STORAGE_ACCOUNT_URL`, `CENTINELA_RAW_CONTAINER` |
| `README.md` / `app_service_help.md` / `APP_SERVICE_DEPLOYMENT.md` | `AZURE_STORAGE_ACCOUNT_NAME`, y las propiedades `azure.storage.account-name` / `azure.storage.container-name` |

**Recomendación práctica:** define ambos juegos de variables en tu entorno local mientras se confirma cuál es el vigente, o revisa `AzureClientsConfig` / `IngestaProperties` directamente en el código antes de tu primera prueba contra Azure real. Documenta la respuesta en este mismo archivo una vez confirmada, para que no se vuelva a perder.

```bash
# Perfil azure (segun Claude.md)
export CENTINELA_STORAGE_ACCOUNT_URL="https://<tu-cuenta>.blob.core.windows.net"
export CENTINELA_RAW_CONTAINER="evidencias-financieras-privado"

# Alternativa (segun README / app_service_help)
export AZURE_STORAGE_ACCOUNT_NAME="<tu-cuenta>"
```

Ninguna de las dos rutas requiere una clave de acceso: la autenticación siempre pasa por `DefaultAzureCredential` (Managed Identity en Azure, `az login` en local).

## 1.3 Compilar y correr tests

```bash
cd layered-minimal   # o la carpeta raíz del módulo, según tu checkout

mvn test                        # perfil "local", sin Azure — unit + MockMvc
mvn clean package -DskipTests   # genera el jar ejecutable
```

El artefacto queda en:

```
target/ingesta-layered-minimal-0.0.1-SNAPSHOT.jar
```

## 1.4 Correr localmente (perfil `local`, sin Azure)

```bash
mvn spring-boot:run
```

Perfil por defecto: **`local`**, usa `InMemoryRawTransactionStore` (no toca Azure) y `NoOpTransactionEventPublisher` (no publica nada realmente — es el punto de inserción para la Semana 2, ver Parte 2.6). API disponible en `http://localhost:8080`.

## 1.5 Probar la SPI de ingesta (curl, con los campos exactos del contrato)

### 1.5.1 Ingestar una transacción nueva

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

### 1.5.2 Reenviar la misma transacción (idempotencia)

Repite exactamente el mismo `curl`. Respuesta esperada: **`200 OK`** con `"status": "YA_RECIBIDA"` y el **mismo** `ingestedAt` de la primera vez (no se sobreescribe, no se vuelve a publicar el evento — ver Parte 2.5).

### 1.5.3 Consultar una transacción

```bash
curl -i http://localhost:8080/api/v1/transactions/tx-1001
```

`200 OK` si existe, `404 Not Found` si no.

### 1.5.4 Casos de prueba de validación del contrato (para probar el rechazo)

| Caso | Campo afectado | Resultado esperado |
|---|---|---|
| `amount` negativo o cero | `amount` | `400 Bad Request` |
| `currency` con más o menos de 3 caracteres | `currency` | `400 Bad Request` |
| `latitude` fuera de [-90, 90] o `longitude` fuera de [-180, 180] | `latitude` / `longitude` | `400 Bad Request` |
| `occurredAt` en el futuro | `occurredAt` | `400 Bad Request` |
| Campo no contemplado en el contrato (ej. `foo: "bar"`) | cualquiera | `400 Bad Request` — **se rechaza, no se ignora** (`fail-on-unknown-properties`, ver 2.4) |
| Falta un campo obligatorio | cualquiera | `400 Bad Request` |

### 1.5.5 Subir evidencia (endpoint disponible en esta API, sin relación con scoring)

```bash
curl -i -X POST http://localhost:8080/api/v1/transactions/tx-1001/evidencias \
  -F "file=@/ruta/local/comprobante.pdf"
```

`201 Created` si el archivo es un PDF/PNG real (validación por magic number) y ≤ 5 MB; `400 Bad Request` en caso contrario; `404 Not Found` si `transactionId` no existe.

## 1.6 Qué **no** puedes probar en este repo todavía

Según `Claude.md` (sección 2), este repositorio **cubre únicamente la ingesta**. Estas cosas **no** se ejecutan aquí, ni con curl ni de ninguna otra forma, porque no viven en este código:

- Cálculo de score de una transacción.
- Apertura de un `FraudCase`.
- Consulta de reglas activadas o de explicación.

Son responsabilidad del motor de scoring serverless (Semana 2), que reacciona a un evento — ver Parte 2, sección 2.6 y 2.7 para el diseño objetivo, y la Parte 5 para cómo probar el desacoplamiento cuando ese componente exista.

## 1.7 Cheatsheet

```bash
mvn test                                   # unit + MockMvc, perfil local
mvn clean package -DskipTests              # genera el jar
mvn spring-boot:run                        # levanta local, sin Azure
az webapp deploy --resource-group <rg> --name <app> --src-path target/ingesta-layered-minimal-0.0.1-SNAPSHOT.jar --type jar
```

---

# PARTE 2 — LA SPI DE INGESTA: EXPLICACIÓN PASO A PASO Y POR QUÉ

## 2.1 Qué cubre exactamente este repositorio

Según `Claude.md`, el alcance de este repo es **únicamente el requerimiento 2.9 de la Semana 1: la API de ingesta de transacciones.** Su responsabilidad, en este orden exacto y sin excepción:

1. Recibir el payload.
2. Validar el cumplimiento del contrato.
3. Persistir la transacción cruda.
4. Responder con acuse de recibo.

**Lo que esta API nunca debe hacer, en ninguna semana** (cita literal de la regla de diseño): consultar historial de cuentas, calcular score, aplicar reglas de riesgo, ni abrir casos. Esa es responsabilidad exclusiva del motor de scoring asíncrono de la Semana 2, que reacciona a un evento — nunca a una llamada síncrona desde este servicio.

Esta restricción no es un detalle de estilo: es la traducción directa de la restricción de negocio central de todo el proyecto (`Centinela-spec.md`): **"el cliente no puede esperar"**. Si el cliente pasa su tarjeta, la respuesta debe ser inmediata; es inaceptable que la transacción quede colgada mientras el sistema consulta historial, aplica reglas y abre un caso.

## 2.2 Arquitectura del servicio (puertos y adaptadores)

```
com.ingesta
├── api            # HTTP <-> dominio. Sin logica de negocio.
│                  #   TransactionController, GlobalExceptionHandler,
│                  #   dto/ (TransactionRequest, TransactionAckResponse, ApiErrorResponse)
├── domain         # TransactionEvent (contrato canonico) + excepciones de negocio
├── service        # TransactionIngestionService (orquesta el caso de uso),
│                  # TransactionValidator (reglas que dependen de config/tiempo)
├── persistence    # Puerto RawTransactionStore + adaptadores:
│                  #   InMemoryRawTransactionStore (perfil local)
│                  #   BlobRawTransactionStore (perfil azure, Managed Identity)
├── messaging      # Puerto TransactionEventPublisher (PUNTO DE INSERCION SEMANA 2)
│                  #   + NoOpTransactionEventPublisher (unica impl. de Semana 1)
└── config         # IngestaProperties, ClockConfig, AzureClientsConfig
```

**Regla de dependencia:** `api → service → (domain, persistence, messaging)`. El controlador no conoce Azure ni ningún detalle de infraestructura, solo interfaces.

**Por qué puertos y adaptadores (hexagonal-lite):** cada integración externa (storage, mensajería) es una interfaz + implementaciones intercambiables por perfil Spring (`local` / `azure`, vía `@ConditionalOnProperty`). Esto permite:
- Correr y probar toda la lógica de negocio sin depender de una suscripción de Azure real (perfil `local`).
- Cambiar de almacenamiento en memoria a Blob Storage, o de "no publicar nada" a una implementación real de mensajería (Semana 2), sin tocar el `service` ni el `api`.
- Que un desarrollador nuevo entienda de un vistazo dónde termina la lógica de negocio y dónde empieza la integración externa.

## 2.3 El contrato de la transacción — la pieza que no se toca sin avisar

Definido en `TransactionRequest` (entrada HTTP) y `TransactionEvent` (modelo canónico: lo que se persiste y luego se publica). Es un **compromiso vinculante entre componentes**: cambiarlo en la Semana 2 obliga a tocar simultáneamente la API, la mensajería, el motor de scoring y los almacenes.

| Campo | Tipo | Quién lo define | Por qué |
|---|---|---|---|
| `transactionId` | `String` | Cliente | Es la clave de idempotencia (ver 2.5) |
| `accountId` | `String` | Cliente | Clave de partición para el historial (crítico en Semana 2) |
| `amount` | `BigDecimal` | Cliente | **Nunca `double`/`float` para dinero** — evita errores de redondeo |
| `currency` | `String` (ISO 4217) | Cliente | Estándar internacional de código de moneda |
| `occurredAt` | timestamp | Cliente | **Solo referencial** — ver por qué a continuación |
| `latitude` / `longitude` | grados decimales (WGS84) | Cliente | Para permitir cálculo de distancia (Haversine) en Semana 2 |
| `merchantId` / `merchantCategory` | `String` | Cliente | Insumo para la regla de comercio de riesgo (Semana 2) |
| `ingestedAt` | timestamp UTC | **Servidor** (autoritativo) | Ver por qué a continuación |

### 2.3.1 Por qué `ingestedAt` (servidor) y no `occurredAt` (cliente) para las reglas de seguridad

Decisión ya tomada y explícitamente marcada como "no se reabre sin motivo nuevo": `ingestedAt` es el único timestamp que usarán las reglas de velocidad y geo-imposible en la Semana 2. `occurredAt` es solo referencial.

**Por qué:** confiar en el reloj del cliente para scoring de seguridad es explotable — un atacante controla completamente lo que envía en el payload, incluyendo un `occurredAt` falso diseñado para evadir la regla de velocidad o la de geo-imposible. El reloj del servidor (`ClockConfig`, inyectado como `Clock` para ser testeable) es la única fuente de tiempo confiable para decisiones de seguridad.

### 2.3.2 Por qué el `transactionId` lo asigna el cliente, no el servidor

Es la clave de idempotencia. Si el servidor generara el id, dos envíos de la misma transacción física (por un reintento de red del sistema origen) se verían como dos transacciones distintas — exactamente lo que la idempotencia busca evitar.

## 2.4 Validación del contrato — rechazar, no ignorar

Los campos no contemplados en el contrato se **rechazan con 400**, nunca se ignoran silenciosamente. Esto está fijado como una decisión que no se cambia sin que el usuario lo pida explícitamente (`fail-on-unknown-properties` no se vuelve `false` "de paso").

**Por qué importa en un contexto financiero:** un campo desconocido silenciosamente ignorado puede ser evidencia de que el sistema origen está enviando un contrato distinto al que ambos lados creen tener — mejor fallar ruidosamente en integración que descubrirlo en producción con datos corruptos o incompletos.

## 2.5 Idempotencia — el punto exacto donde se confirma al cliente

Cita literal de `Claude.md`: el punto seguro para confirmar aceptación al cliente es **inmediatamente después** de que `RawTransactionStore.saveIfAbsent(...)` confirma la escritura, **antes** de invocar `TransactionEventPublisher.publish(...)`.

Secuencia exacta (`TransactionIngestionService.ingest(...)`):

1. Recibir el payload.
2. Validar el cumplimiento del contrato.
3. Persistir la transacción cruda (`saveIfAbsent`).
4. Publicar el evento de transacción.
5. Responder con acuse de recibo.

Si `transactionId` ya existía: **no se sobrescribe, no se vuelve a publicar el evento**, la API responde `200 OK` con `status: "YA_RECIBIDA"` (distinto del `202 Accepted` de una transacción nueva).

**Por qué no publicar de nuevo el evento en un duplicado:** si se publicara otra vez, el motor de scoring de la Semana 2 procesaría la misma transacción física dos veces, contaminando las reglas de velocidad y monto con datos duplicados, y potencialmente abriendo dos casos de fraude para el mismo hecho.

## 2.6 El punto de inserción de la Semana 2: `TransactionEventPublisher`

Esta es, arquitectónicamente, la pieza más importante de todo el repo, aunque hoy solo tiene una implementación vacía (`NoOpTransactionEventPublisher`).

```
messaging/
├── TransactionEventPublisher       (interfaz — el "puerto")
└── NoOpTransactionEventPublisher   (unica implementacion de Semana 1)
```

**Qué hace hoy:** nada real. Es un adaptador que cumple el contrato de la interfaz sin publicar a ningún sistema de mensajería externo.

**Qué va a pasar en la Semana 2:** se agrega una implementación real (Service Bus / Storage Queue, según decisión de arquitectura) que sí publica el `TransactionEvent`. El motor de scoring serverless (fuera de este repo) reacciona a ese evento.

**Por qué está diseñado así desde ahora, aunque no haga nada:** decisión explícita documentada — *"No se implementa publicación real de eventos aquí todavía... agregar la implementación real es tarea explícita de Semana 2, no algo que se cuela 'de paso' en un cambio de la API."* Esto evita dos errores comunes: (a) que alguien intente "adelantar trabajo" acoplando la API directamente al motor de scoring (el error de diseño más frecuente, según `semana2-spec.md` sección 5), y (b) que el contrato de publicación cambie sin que quede documentado como una decisión de Semana 2.

## 2.7 Recorrido completo de una transacción en el sistema (más allá de este repo)

Aunque este repo solo implementa el paso 1, entender el flujo completo (`Centinela-spec.md`) es indispensable para no romper el contrato con lo que viene después:

1. **Ingesta** (este repo). La API recibe la transacción y responde de inmediato con un acuse. No espera al análisis.
2. **Publicación del evento.** Aquí termina la responsabilidad de la API.
3. **Scoring.** Un componente serverless reacciona al evento, consulta historial reciente de la cuenta, aplica las reglas heurísticas y calcula el score.
4. **Decisión.** Si el score supera el umbral, se encola un caso.
5. **Apertura del caso** en la base de datos de gestión.
6. **Explicación** legible y determinista (plantilla, sin LLM) del porqué de la marca.
7. **Resolución** por un analista humano. Todo queda auditado.

**El cliente ya recibió su respuesta en el paso 1.** Si una arquitectura hace que el cliente espere por el paso 6, está mal diseñada — esta es literalmente la frase con la que cierra la especificación del proyecto.

---

# PARTE 3 — MOTOR DE SCORING-RULES: DISEÑO OBJETIVO (SEMANA 2)

> ⚠️ **Importante:** a diferencia de la Parte 2 (que documenta código que existe hoy en este repo), esta sección documenta el **diseño especificado** para el motor de scoring según `Centinela-spec.md` y `semana2-spec.md`. Es la especificación que el equipo debe construir, típicamente como un componente **serverless separado**, no dentro de este repositorio de ingesta. Documentarlo aquí, junto a la ingesta, es intencional: son las dos mitades de un mismo contrato y cualquier persona que trabaje en una necesita entender la otra.

## 3.1 Disparador y secuencia de ejecución

Componente serverless activado por el evento de transacción entrante (el mismo `TransactionEvent` publicado por `TransactionEventPublisher` en la Parte 2.6). Secuencia:

1. Recibir el evento de transacción.
2. Consultar el historial reciente de la cuenta en el almacén de transacciones.
3. Evaluar las cuatro reglas de detección.
4. Sumar los puntos de las reglas activadas.
5. Persistir el score y el detalle de las reglas activadas junto a la transacción.
6. Si el score supera el umbral, publicar un mensaje de apertura de caso (mecanismo de mensajería **distinto** al del paso 2 de la ingesta — ver 3.5).

## 3.2 Las cuatro reglas de detección

| Regla | Criterio de evaluación |
|---|---|
| **Velocidad** | Cantidad de transacciones de la cuenta dentro de una ventana temporal corta (ej. 8 compras en 3 minutos). |
| **Monto atípico** | Desviación del monto respecto al comportamiento histórico de la cuenta (ej. la cuenta suele mover $50.000 y de repente intenta $4.000.000). |
| **Geo-imposible** | Relación entre la distancia que separa dos transacciones consecutivas y el tiempo transcurrido entre ellas (ej. Medellín y Madrid con 10 minutos de diferencia). |
| **Comercio de riesgo** | Pertenencia del comercio o categoría destino a la lista de entidades marcadas previamente como sospechosas. |

Cada regla que se dispara **suma puntos** al score de la transacción. No hay Machine Learning — son reglas heurísticas que el equipo escribe, entiende y puede defender.

## 3.3 El umbral — requisito de diseño, no un número quemado en el código

**Requerimiento explícito:** el umbral no puede ser un literal en el código. Su modificación **no puede requerir un nuevo despliegue** (típicamente resuelto con `@ConfigurationProperties` externa, o configuración remota si el componente es serverless).

**Compromiso a justificar:** un umbral muy bajo genera falsos positivos y satura a los analistas; uno muy alto deja pasar fraude real. El valor elegido debe defenderse explícitamente en el documento de arquitectura del equipo, no asumirse.

## 3.4 Guardar el porqué, no solo el cuánto

**Requisito no negociable:** cada regla que se dispara debe registrar **por qué** se disparó, con los datos concretos que la activaron. No basta con guardar `score: 85`.

Estructura mínima de registro por regla activada:

- Identificador de la regla.
- Puntos aportados.
- Valores concretos observados que justificaron la activación (ej. cantidad de transacciones en la ventana, monto observado frente a promedio histórico, distancia y tiempo transcurrido).

**Por qué importa tanto:** el explicador de casos (Semana 3) se construye enteramente sobre esta información. Ejemplo del texto que debe poder generarse a partir de ese detalle guardado (plantilla determinista, **sin LLM**):

> *Transacción marcada con score 82 (umbral: 60).*
> *Se detectaron 3 transacciones de esta cuenta en los últimos 4 minutos, cuando el promedio es de 1 cada 6 horas (+35 puntos).*
> *El monto de $4.200.000 supera en 84× el promedio histórico de la cuenta ($50.000) (+30 puntos).*
> *La transacción anterior de esta cuenta se originó en Medellín hace 11 minutos; esta se origina en Madrid, a 8.000 km (+17 puntos).*

Si el motor no logra producir un texto así a partir de sus datos guardados, es señal de que está guardando de menos.

## 3.5 Los dos mecanismos de mensajería — no son intercambiables

`semana2-spec.md` exige distinguir explícitamente dos propósitos distintos de mensajería:

| Mecanismo | Propósito | Requisito clave |
|---|---|---|
| **Distribución del evento de transacción** | La API publica un evento tras persistir la transacción y termina. El motor de scoring reacciona de forma independiente. La API no conoce ni espera el resultado. | Notifica la *ocurrencia* de un evento — no necesita garantizar que alguien lo procese en ese instante. |
| **Cola de casos marcados** | El motor de scoring encola los casos que superan el umbral. El flujo de gestión de casos los consume a su propio ritmo. | Debe **garantizar** que ningún caso se pierda ante la indisponibilidad del consumidor — requiere durabilidad. |

**Prueba de validación obligatoria:** con el consumidor de casos detenido, la API debe seguir recibiendo y respondiendo transacciones con normalidad. Al restablecerse el consumidor, todos los casos marcados durante la indisponibilidad deben procesarse sin pérdida.

**La diferencia conceptual que hay que documentar y defender:** *notificar la ocurrencia de un evento* (mecanismo 1, tolerante a que a veces nadie escuche en el instante exacto) vs. *garantizar el procesamiento* (mecanismo 2, no puede perder mensajes bajo ninguna circunstancia, porque cada uno representa un caso de fraude potencialmente real esperando revisión humana).

## 3.6 Dónde vive cada dato — y por qué eso condiciona todo lo demás

### 3.6.1 Almacén de transacciones y scores

Perfil de carga: escritura constante de alto volumen, y **una consulta que domina todo lo demás**: "dame las transacciones recientes de esta cuenta" — el motor la ejecuta en **cada** transacción procesada.

Requisitos de diseño:

- **Clave de partición**: debe permitir recuperar el historial de una cuenta sin recorrer particiones ajenas (candidato natural: `accountId`). **No admite modificación posterior sin migración completa** — se define antes de la primera escritura. La justificación escrita debe indicar qué consulta optimiza y cuál sacrifica.
- **Nivel de consistencia**: elegir y justificar el compromiso entre garantía de lectura y latencia; documentar si el caso de uso realmente requiere consistencia fuerte.
- **Política de expiración (TTL)**: eliminar automáticamente registros que ya no aportan al análisis, en función de las ventanas temporales que usan las reglas (ej. si la regla de velocidad mira una ventana de minutos y la de monto atípico un histórico más largo, el TTL debe cubrir la ventana más amplia que realmente se consulta).
- **Nivel de servicio gratuito**: documentar sus límites de capacidad y almacenamiento.

### 3.6.2 Almacén de casos de fraude

Relacional, con integridad referencial, reportería y trazabilidad. Modelo mínimo:

| Entidad | Contenido |
|---|---|
| Caso | Referencia a la transacción, score obtenido, estado actual, fecha de apertura |
| Estado | Catálogo de estados posibles del caso |
| Asignación | Relación caso–analista |
| Resolución | Decisión final, analista responsable, fecha, observaciones |
| Auditoría | Registro inmutable de cada cambio de estado: qué cambió, quién y cuándo |

Requisitos: acceso restringido a la subred de aplicación (no alcanzable desde internet), estrategia de respaldo documentada (periodicidad, retención, pérdida máxima tolerable), y uso del nivel de servicio gratuito con sus límites documentados.

### 3.6.3 Documentos de verificación de identidad

Archivos binarios que los analistas suben al escalar un caso — se escriben una vez y se leen pocas veces (perfil de acceso muy distinto al de los otros dos almacenes, lo que justifica usar Blob Storage en vez de una base de datos para este dato específico).

## 3.7 Seguridad y costo del motor de scoring

- **Secretos**: ninguna cadena de conexión, clave o credencial en código ni en variables configuradas manualmente — todo vía gestor de secretos (Key Vault), con los componentes autenticándose contra él mediante identidad gestionada (no una credencial destinada a obtener credenciales).
- **Control de tasa (rate limiting)** en la ingesta: la API está expuesta a internet; cada petición aceptada dispara un evento y una ejecución del motor de scoring, con el consiguiente consumo de crédito. Sin límite de tasa, un actor malicioso puede saturar la API con transacciones sintéticas y agotar el presupuesto del proyecto. Se implementa en la aplicación o vía los mecanismos del servicio de aplicaciones (no hay una capa de API Management dedicada en este proyecto).
- **Presupuesto**: el proyecto corre sobre una suscripción gratuita (200 USD / 30 días de reloj). Meta explícita: terminar habiendo gastado menos de 60 de los 200 USD. Al cierre de la Semana 2, el crédito acumulado debe ser inferior a 40 USD.

## 3.8 El error de diseño más frecuente

Cita literal de `semana2-spec.md`: **"La invocación directa del motor de scoring desde la API constituye el error de diseño más frecuente en esta semana. Produce un sistema que funciona y que incumple el requisito arquitectónico central."** Toda la Semana 3 (explicador, observabilidad) se construye sobre el supuesto de que el pipeline está desacoplado — si alguien "atajó" este requisito conectando la API directo al scoring, hay que deshacerlo antes de seguir, no seguir construyendo encima.

---

# PARTE 4 — DESPLIEGUE (WEBAPP / APP SERVICE)

## 4.1 Runtime de producción: Java 11

**Corrección importante frente a documentación previa:** el runtime real de este repo hoy es **Java 11 + Spring Boot 2.7.18**, no Java 21 / Spring Boot 3.3 (ver Parte 5 para el detalle completo de por qué y qué cambió). La versión de Java que ejecuta el jar en Azure App Service la controla **únicamente el "Stack settings" del recurso en el portal** — un `system.properties` en el repo (convención de Heroku) **no hace nada en Azure App Service**; si ves ese archivo o una mención a él en documentación vieja, es un error que ya fue corregido.

## 4.2 Preparar el repositorio

```powershell
mvn clean package -DskipTests
```

Jar generado en `target/ingesta-layered-minimal-0.0.1-SNAPSHOT.jar`.

## 4.3 Opciones de despliegue

### Opción A — GitHub Deployment Center (recomendada si ya tienes App Service creado)

1. Portal de Azure → tu App Service → `Deployment Center`.
2. Fuente: `GitHub`. Autoriza y selecciona este repositorio.
3. Elige la rama a desplegar — según `APP_SERVICE_DEPLOYMENT.md`, la rama `feature/java11` es la que corresponde al runtime Java 11 actual.
4. Guarda y espera a que se cree el flujo de despliegue (build de Maven como parte del pipeline).

### Opción B — Despliegue manual con Azure CLI

```powershell
az webapp deploy --resource-group <resource-group> --name <app-service-name> --src-path target/ingesta-layered-minimal-0.0.1-SNAPSHOT.jar --type jar
```

## 4.4 Configuración en App Service

**Startup Command:**
```bash
java -jar /home/site/wwwroot/app.jar
```
(ajusta el nombre del jar si el despliegue lo sube con otro nombre).

**Puerto:** la app escucha en 8080 internamente; Azure App Service inyecta el puerto correcto vía la variable `PORT` — para una app Java simple normalmente basta con dejar el arranque por defecto.

**Variables de entorno recomendadas** (ver también el aviso de la Parte 1.2 sobre la discrepancia de nombres entre fuentes):

```text
AZURE_CLIENT_ID=
AZURE_TENANT_ID=
AZURE_CLIENT_SECRET=
azure.storage.account-name=<nombre-de-tu-cuenta>
azure.storage.container-name=evidencias-financieras-privado
```

Si usas identidad gestionada en App Service, las credenciales de Azure Identity se resuelven por el proveedor por defecto — **no hace falta guardar claves en el repositorio ni en estas variables**.

## 4.5 Verificación post-despliegue

```text
https://<tu-app-service>.azurewebsites.net/
https://<tu-app-service>.azurewebsites.net/api/v1/transactions
```

Envía el payload de ejemplo de la Parte 1.5.1: `202 Accepted` con `status: "RECIBIDA"` confirma que el despliegue funciona; repetir el mismo `transactionId` debe devolver `200 OK` con `status: "YA_RECIBIDA"`.

## 4.6 Notas operativas

- El contrato rechaza campos no contemplados (ver 2.4) — no lo cambies para "hacer pasar" un payload distinto sin actualizar primero el contrato documentado.
- La validación de evidencia usa Azure Blob Storage con identidad gestionada — nunca claves de cuenta.
- La API no debe bloquearse por el análisis de fraude; solo recibe, valida y persiste (ver Parte 2.1).

---

# PARTE 5 — REGISTRO DE MIGRACIÓN: DE JAVA 21 A JAVA 11

Contexto necesario para cualquiera que vaya a tocar el `pom.xml` o el código base: por qué el proyecto corre hoy en una versión de Java más antigua de la que un desarrollador moderno esperaría, y qué se sacrificó al bajarla.

## 5.1 Por qué no bastaba con cambiar un número de versión

El código usaba tres construcciones de Java que **no existen en Java 11**, sin importar qué diga el `pom.xml`:

| Construcción | Apareció en | Dónde se usaba |
|---|---|---|
| `record` | Java 16 | Modelos y DTOs (transacción, request, response, error, evidencia) |
| `instanceof X x` (con patrón) | Java 16 | `GlobalExceptionHandler` |
| *text blocks* (`"""`) | Java 15 | Tests de integración |

Además, Spring Boot 3.x requiere Java 17 como mínimo — para Java 11 hubo que bajar también el framework a **Spring Boot 2.7.18** (última versión estable de la línea 2.x que soporta Java 11; ya no recibe parches, pero es la más reciente compatible).

## 5.2 Qué cambió en el código, y qué se preservó a propósito

- **Los `record` se volvieron clases normales**, pero los métodos de acceso se mantuvieron con el mismo nombre que tenían como componentes de record (ej. `transaction.transactionId()`, no `getTransactionId()`) — precisamente para que el código que ya los consumía (servicios, repositorios) **no necesitara ningún cambio**.
- Se agregó `@JsonProperty` en cada método de acceso (Jackson no reconoce automáticamente accesores que no siguen la convención JavaBean `getXxx()`), y `@JsonCreator` + `@JsonProperty` en el constructor de la clase que Jackson debe *construir* desde el JSON entrante (el request de transacción) — las demás clases solo se *devuelven*, nunca se arman desde JSON, así que no lo necesitan.
- Las anotaciones de validación (`@NotBlank`, `@NotNull`, `@DecimalMin`, `@DecimalMax`, `@Size`) no cambiaron de comportamiento, solo de ubicación sintáctica (de "componentes de record" a "campos de clase").
- El `instanceof X x` con patrón se separó en comprobación + cast explícito (sintaxis pre-Java 16), sin cambio de comportamiento.
- Los *text blocks* de los tests se volvieron concatenación de strings — solo afecta al test, no a la aplicación.
- `jakarta.validation` volvió a `javax.validation` (Spring Boot 2.7.x usa el namespace viejo) — cambio mecánico de import, comportamiento idéntico.

## 5.3 Qué NO cambió

Componentes que no usaban ninguna sintaxis incompatible con Java 11 se copiaron tal cual: la lógica de negocio, las rutas, los códigos de estado, la validación de archivos por *magic numbers* en el servicio de evidencias, y `application.properties`.

## 5.4 Corrección de un error real en la guía de despliegue anterior

La versión previa de `APP_SERVICE_DEPLOYMENT.md` mencionaba un `system.properties` con `java.runtime.version=11` como si controlara la versión de Java en Azure. **Es una convención de Heroku — ese archivo no hace nada en App Service.** En Azure, la versión de Java la controla únicamente el "Stack settings" del recurso en el portal.

## 5.5 Cuándo reabrir esta decisión

Si el App Service se mueve a Java 17 o superior, es un buen momento para considerar revertir esta migración y volver a Spring Boot 3.x — pero mientras el App Service esté fijo en Java 11, no se debe tocar el `pom.xml` en esa dirección sin repetir este mismo análisis (las tres construcciones de sintaxis de la tabla 5.1 volverían a romper el build si se sube el `java.version` sin subir también el framework).

---

# PARTE 6 — TABLA RESUMEN DE FUENTES Y QUÉ CUBRE CADA UNA

| Documento | Qué información aporta a este archivo |
|---|---|
| `Claude.md` | Arquitectura oficial del repo (puertos/adaptadores), contrato de la transacción, idempotencia, punto de inserción de mensajería, decisiones que no se reabren |
| `Centinela-spec.md` | Alcance completo del proyecto (3 semanas), las 4 reglas de scoring, ejemplo del explicador, actores, dónde vive cada dato, presupuesto |
| `semana2-spec.md` | Especificación detallada del motor de scoring, los dos mecanismos de mensajería, requisitos de almacenamiento, criterios de aceptación |
| `error.md` | Registro completo de la migración Java 21 → Java 11 / Spring Boot 3.3 → 2.7.18 |
| `README.md` | Endpoints actuales, códigos de respuesta, comandos básicos |
| `APP_SERVICE_DEPLOYMENT.md` / `app_service_help.md` | Opciones de despliegue a App Service, variables de entorno, verificación |

**Discrepancias detectadas entre fuentes que quedan pendientes de confirmar con el equipo** (ver también Parte 1.2): el nombre de las variables de entorno para el Storage Account en perfil `azure` (`CENTINELA_STORAGE_ACCOUNT_URL`/`CENTINELA_RAW_CONTAINER` según `Claude.md`, vs. `AZURE_STORAGE_ACCOUNT_NAME`/`azure.storage.account-name` según el resto de fuentes). Antes de desplegar contra Azure real, verifica cuál es la que efectivamente lee `AzureClientsConfig`/`IngestaProperties` en el código actual.
