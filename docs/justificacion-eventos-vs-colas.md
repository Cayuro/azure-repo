# Por que la notificacion de eventos y la cola de casos son mecanismos distintos

Ambos usan Azure Storage Queue, pero cumplen roles arquitectonicos distintos y por eso
el proyecto los separa en dos colas, dos interfaces (`TransactionEventPublisher` /
`FraudCaseEventPublisher`) y dos implementaciones concretas, en vez de un publisher
generico unico.

## `cola-transacciones-ingesta`: notificacion (event notification)

- **Proposito**: avisar que "se ingirio una transaccion" para desacoplar la API del
  computo de scoring (ver `AzureQueueTransactionEventPublisher`). Al productor no le
  importa si, cuando o como se consume el aviso.
- **Contrato de entrega**: best-effort. Si falla la publicacion, se registra el error y
  no se propaga ni se reintenta desde el productor, porque la transaccion ya quedo
  persistida de forma durable en el repositorio: esa es la fuente de verdad, el evento
  es solo un disparador para que el scoring ocurra pronto, no el dato en si.
- **Consecuencia de perder el mensaje**: degradada pero recuperable. Una transaccion
  podria tardar en dispararse hacia scoring, pero puede reconciliarse despues (por
  ejemplo, iterando transacciones sin score asociado).

## `cola-casos-fraude`: cola de casos (garantia)

- **Proposito**: entregar un item de trabajo que debe completarse: un caso ya
  identificado como fraude potencial (score > umbral), pendiente de revision por el
  equipo analitico (ver `AzureQueueFraudCaseEventPublisher`).
- **Contrato de entrega**: garantizado. "Ningun caso se pierda ante indisponibilidad
  del consumidor" es un requisito de negocio/cumplimiento explicito, no una operacion
  best-effort. Esta garantia la da la propia Storage Queue: el mensaje no se borra
  hasta que el consumidor confirma el procesamiento (`DeleteMessage`); si el consumidor
  esta caido o crashea a mitad de proceso, el mensaje solo queda invisible durante el
  `visibilityTimeout` y vuelve a aparecer para reintento (ver
  `docs/resiliencia-cola-ingesta.md`).
- **Consecuencia de perder el mensaje**: un fraude potencial sin revisar, con impacto
  financiero/regulatorio directo, no una simple degradacion.

## Por que no comparten la misma cola

1. **Criticidad distinta exige monitoreo distinto**: backlog en la cola de notificacion
   es tolerable; backlog en la cola de casos es una alerta inmediata (potencial fraude
   sin atender). Cada una necesita su propia poison queue y su propio umbral de alerta
   sobre `ApproximateMessagesCount`.
2. **RBAC distinto**: un caso de fraude es informacion mas sensible que "se recibio una
   transaccion". Separar las colas permite otorgar acceso de lectura solo al equipo
   analitico sobre `cola-casos-fraude`, sin darle visibilidad sobre todo el trafico de
   ingesta.
3. **Acoplar ambas semanticas en una sola cola** obligaria al consumidor a filtrar
   mensajes de naturaleza distinta (evento informativo vs. item accionable), mezclando
   dos responsabilidades que no deberian compartir el mismo nivel de garantia ni el
   mismo proceso de revision operativa.

En resumen: misma tecnologia (Azure Storage Queue), pero un rol de **notificacion**
(fire-and-forget, para desacoplar y ganar latencia) y un rol de **cola de trabajo
durable** (garantia dura de entrega) no deben resolverse con el mismo mecanismo.
