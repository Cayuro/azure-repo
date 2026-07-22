# Resiliencia de la cola de ingesta (Azure Storage Queue)

Documenta el comportamiento esperado de la cola `cola-transacciones-ingesta` (y su cola
de poison messages `cola-transacciones-ingesta-poison`) usada para procesar las
transacciones/evidencias de forma asincrona.

Estas dos colas siguen la convencion `{nombre}` / `{nombre}-poison` que usa de forma
nativa el **Queue trigger de Azure Functions / WebJobs SDK** sobre Azure Storage Queue:
si el consumidor esta implementado con ese binding, el movimiento a la cola poison es
automatico (no hay que programarlo). Si el consumidor usa el SDK de Storage Queues
directamente (sin Functions), ese movimiento hay que implementarlo a mano segun se
describe en el punto 2.

## 1. El consumidor falla antes de confirmar

- Storage Queue no usa PeekLock: al leer un mensaje (`GetMessages`/`ReceiveMessages`)
  este queda invisible en la cola durante un `visibilityTimeout` (30s por defecto), pero
  sigue existiendo. Solo desaparece de verdad cuando el consumidor llama `DeleteMessage`
  tras procesarlo con exito.
- Si el consumidor se cae o crashea antes de borrar el mensaje (o, en Azure Functions, si
  la funcion lanza una excepcion no controlada), el `DeleteMessage` nunca ocurre.
- Azure Functions renueva el `visibilityTimeout` automaticamente mientras la funcion
  sigue corriendo, pero si el proceso muere (crash, OOM, reinicio del host) esa renovacion
  se detiene; al expirar el timeout el mensaje vuelve a quedar visible en la cola y puede
  ser tomado de nuevo por cualquier instancia del consumidor.
- Esto implica entrega **at-least-once**: el mensaje no se pierde, pero puede haber sido
  parcialmente procesado (efecto secundario ya aplicado) sin haberse confirmado.
- Consecuencia de diseno: el procesamiento debe ser **idempotente**. En este proyecto eso
  ya se resuelve a nivel de `transactionId` (ver `TransactionRepository.saveIfAbsent`, que
  distingue `CREATED` de `ALREADY_EXISTS`), asi un reproceso no duplica efectos.
- Cada vez que el mensaje vuelve a quedar visible y se vuelve a leer, se incrementa su
  contador `DequeueCount`.

## 2. El mensaje falla reiteradamente (poison message)

- `DequeueCount` sube en cada intento fallido de procesamiento (crash, excepcion, timeout
  de visibilidad cumplido sin `Delete`).
- A diferencia de Service Bus, **Storage Queue no tiene dead-lettering nativo**: el
  movimiento a `cola-transacciones-ingesta-poison` lo hace:
  - **Automaticamente** el runtime de Azure Functions/WebJobs, cuando `DequeueCount`
    supera `maxDequeueCount` (5 por defecto, configurable en `host.json` bajo
    `extensions.queues.maxDequeueCount`). El runtime borra el mensaje de
    `cola-transacciones-ingesta` y lo inserta tal cual en `cola-transacciones-ingesta-poison`.
  - **Manualmente**, si el consumidor usa el SDK de Storage Queues sin el binding de
    Functions: hay que leer `message.DequeueCount` en cada `ReceiveMessages`, y si supera
    el umbral definido, hacer `SendMessage` a la cola poison (agregando ahi mismo el motivo
    del fallo, ya que a diferencia de Service Bus el mensaje movido no trae automaticamente
    un campo tipo `DeadLetterReason`) y luego `DeleteMessage` en la cola original.
- Se debe monitorear `ApproximateMessagesCount` de `cola-transacciones-ingesta-poison`
  (alerta cuando sea > 0) y tener un proceso, manual o automatizado, de revision/reintento
  de esos mensajes; nunca dejarlos acumularse sin revision.

## 3. La cola crece mas rapido de lo que se vacia

- Se detecta como crecimiento sostenido de `ApproximateMessagesCount` sobre
  `cola-transacciones-ingesta` (tasa de encolado > tasa de `Delete`).
- A diferencia de Service Bus, Storage Queue **no tiene un limite de tamano de cola que
  frene al productor**: el limite real es la capacidad de la cuenta de Storage (hasta
  500 TB) y 64 KB por mensaje, asi que el productor puede seguir encolando indefinidamente
  sin recibir ningun error — el crecimiento no genera backpressure automatica y puede pasar
  desapercibido si no se monitorea activamente.
- Mitigaciones:
  - **Alertas tempranas** en Azure Monitor sobre `ApproximateMessagesCount`/su tendencia de
    crecimiento, ya que Storage Queue no va a rechazar mensajes por si sola como aviso.
  - **Mas concurrencia por instancia**: subir `batchSize` (16 por defecto en `host.json`)
    y `newBatchThreshold` si el cuello de botella es el propio consumidor y no el downstream
    (ej. Azure Blob Storage al guardar evidencias).
  - **Autoescalado de instancias del consumidor** en funcion de la profundidad de la cola
    (Azure Functions en plan Consumption/Premium escala automaticamente segun
    `ApproximateMessagesCount`; en Container Apps/AKS se logra el mismo efecto con un
    scaler de KEDA para Storage Queue).
  - **Backpressure explicito en el productor**: si la cola supera un umbral critico, el
    productor (por ejemplo el endpoint `POST /api/v1/transactions`) deberia rechazar o
    diferir nueva ingesta en vez de seguir encolando ciegamente, ya que aqui nadie mas lo
    va a frenar.
