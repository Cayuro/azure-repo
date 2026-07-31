# email-sender-function

Azure Function (Java 21, Maven) que consume `FraudAlertEvent` desde una Azure
Storage Queue y envía la alerta por correo usando Azure Communication
Services.

**La documentación está centralizada en [`../../docs`](../../docs).**

| Necesitas | Ver |
|---|---|
| Qué hace cada clase, cómo personalizar el correo | [`docs/04-codigo-java.md`](../../docs/04-codigo-java.md) |
| Los servicios de Azure y en qué orden crearlos | [`docs/01-servicios-y-arquitectura.md`](../../docs/01-servicios-y-arquitectura.md) |
| El formato del mensaje de la cola | [`docs/02-formato-mensaje-cola.md`](../../docs/02-formato-mensaje-cola.md) |
| Publicar eventos desde el App Service de scoring | [`docs/03-guia-integracion-productor.md`](../../docs/03-guia-integracion-productor.md) |

## Compilar y desplegar

```bash
mvn clean package
mvn azure-functions:deploy
```

Requiere `local.settings.json` (no se versiona; se crea con los valores que
imprime `docs/scripts/02-communication-services.sh`).

## Probar

```bash
export STORAGE_ACCOUNT=stcolafraudecentinela
bash samples/enqueue_test_message.sh

export RG=rg-centinela-prod FUNCTION_APP_NAME=func-email-sender-centinela
bash samples/query_logs.sh
```
