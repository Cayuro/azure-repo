# Documentación — Notificación de alertas de fraude por correo

Cuando el motor de scoring detecta una transacción sospechosa, publica un
evento en una cola; una Azure Function lo consume y envía un correo a los
analistas.

```
App Service (scoring) → Storage Queue → Azure Function (Java) → Communication Services → correo
```

El código de la Function está en
[`../emailSender/email-sender-function`](../emailSender/email-sender-function).

## Índice

| Documento | Para qué |
|---|---|
| [`01-servicios-y-arquitectura.md`](01-servicios-y-arquitectura.md) | Qué es cada servicio, para qué sirve, cómo se comunican, **en qué orden crearlos**, y las decisiones de diseño con las alternativas que se descartaron y por qué. |
| [`02-formato-mensaje-cola.md`](02-formato-mensaje-cola.md) | El JSON que viaja por la cola: campos, tipos y las reglas que hay que respetar. |
| [`03-guia-integracion-productor.md`](03-guia-integracion-productor.md) | Para quien mantiene el App Service de scoring: cómo publicar eventos en la cola. |
| [`04-codigo-java.md`](04-codigo-java.md) | Las clases de la Function, qué hace cada una, y cómo personalizar el correo. |

## Levantar todo desde cero

```bash
az login
cp docs/.env.example docs/.env      # editar con tus nombres y región

bash docs/scripts/01-storage-queue.sh
bash docs/scripts/02-communication-services.sh
bash docs/scripts/03-app-service-plan.sh
bash docs/scripts/04-function-app.sh
# copiar los valores que imprimió el script 02 a
# emailSender/email-sender-function/local.settings.json
bash docs/scripts/05-app-settings.sh

cd emailSender/email-sender-function && mvn clean package && mvn azure-functions:deploy
```

Un script por servicio, numerados en el orden en que deben ejecutarse (hay
dependencias reales entre ellos: ver
[`01-servicios-y-arquitectura.md`](01-servicios-y-arquitectura.md)). Todos son
idempotentes y cargan `docs/.env` automáticamente.

## Probar que funciona

```bash
export STORAGE_ACCOUNT=stcolafraudecentinela
bash emailSender/email-sender-function/samples/enqueue_test_message.sh

export RG=rg-centinela-prod FUNCTION_APP_NAME=func-email-sender-centinela
bash emailSender/email-sender-function/samples/query_logs.sh
```

## Si no llega el correo

1. **Revisar la cola de poison** — ahí van los mensajes que fallaron 5 veces:
   ```bash
   az storage message peek --queue-name cola-casos-fraude-poison \
     --account-name stcolafraudecentinela --auth-mode login --num-messages 32
   ```
2. **Revisar los logs** con `samples/query_logs.sh`. Si no aparece
   `"EmailNotificationFunction invocada"`, el fallo ocurrió *antes* de llegar
   al código (encoding, permisos o binding).
3. **Revisar spam**: el dominio `*.azurecomm.net` es nuevo y sin reputación.
4. `az webapp log tail` solo muestra eventos de plataforma (contenedor
   arrancando/parando), no trazas de la aplicación. Para eso, Application
   Insights.
