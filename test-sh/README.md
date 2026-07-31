# Pruebas end-to-end: transaccion → correo

Scripts para verificar la cadena completa sin escribir JSON a mano en la terminal.

```
POST /api/v1/transactions
   → scoring (score > 60) → caso de fraude
   → cola-casos-fraude @ stcolafraudecentinela
   → Azure Function → Communication Services → correo
```

## Por que existen estos scripts

Pegar un `az storage message put` con el JSON en línea **no funciona de forma fiable**:
al pegar un comando largo, la terminal introduce saltos de línea reales dentro de los
strings del JSON. Eso es JSON inválido, y la Function lo rechaza con:

```
Illegal unquoted character ((CTRL-CHAR, code 10)): has to be escaped using backslash
  (through reference chain: FraudAlertEvent["activations"]->RuleActivation["description"])
```

Aquí el JSON lo construye `python3` en una sola línea y se valida antes de enviarse, así
que ese fallo no puede ocurrir.

## Uso

| Comando | Qué prueba |
|---|---|
| `bash test-sh/05-estado.sh` | Colas, servicios y permisos. Empieza por aquí. |
| `bash test-sh/01-encolar-alerta.sh` | Solo la mitad derecha: cola → Function → correo. |
| `bash test-sh/02-enviar-transacciones.sh` | La cadena completa, desde la API. |
| `bash test-sh/03-logs-function.sh` | Trazas de la Function (Application Insights). |
| `bash test-sh/04-poison.sh` | Qué falló y por qué. |

Los dos primeros **envían correos reales** a los destinatarios de `FraudAlertRecipients`.

Requisito común: `az login`. Para el `02`, además, la app corriendo.

Orden recomendado: `05` → `01` (si llega el correo, la mitad derecha está sana) → `02`
(la cadena entera). Así cualquier fallo queda acotado a un solo tramo.

## Levantar la app en local (para el `02`)

```bash
cd layered-minimal
mvn spring-boot:run -Dspring-boot.run.jvmArguments="\
 -Dspring.datasource.hikari.initialization-fail-timeout=-1 \
 -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false"
```

Los dos flags evitan que HikariCP bloquee el arranque intentando conectar al
`<your-server>` de `application.properties`. Con el perfil `local` nadie usa el
datasource, así que no se abre ninguna conexión.

Hace falta además rol de datos en Cosmos. **No basta con ser Owner**: Cosmos tiene su
propio RBAC de plano de datos.

```bash
az cosmosdb sql role assignment create \
  --account-name cosmos-centinela-prod -g rg-centinela-prod --scope "/" \
  --principal-id "$(az ad signed-in-user show --query id -o tsv)" \
  --role-definition-id 00000000-0000-0000-0000-000000000002
```

Ojo: con eso, las transacciones de prueba se escriben en la Cosmos de **producción**.

## Contra el App Service desplegado

```bash
API=http://appcentinelaprodgrupo3.azurewebsites.net bash test-sh/02-enviar-transacciones.sh
```

No requiere preparar nada: la Managed Identity ya tiene todos los permisos. Pero solo
sirve una vez el código esté desplegado (Actions despliega al hacer push a `develop`).

## Qué mirar en cada tramo

| Dónde | Señal de éxito |
|---|---|
| Logs de la app | `Alerta de fraude encolada para notificacion por correo. transactionId=... score=102` |
| `03-logs-function.sh` | `invocada` → `Procesando alerta` → `Correo ... enviado` |
| `04-poison.sh` | Vacío |
| Bandeja | El correo (revisa spam: `*.azurecomm.net` es un dominio nuevo) |

Si en los logs de la Function **no** aparece `invocada` pero sí hay mensajes en poison,
el fallo ocurrió antes de llegar al código: encoding, permisos o binding.
