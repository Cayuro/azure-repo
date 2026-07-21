# Ingesta layered minimal

Versi&oacute;n simple de la API de ingesta para entender el flujo con arquitectura layered:

- controller
- service
- repository
- dto
- exception

Las transacciones se guardan en memoria. La carga de evidencias s&iacute; usa Azure Blob Storage
(sin claves, mediante `DefaultAzureCredentialBuilder` / RBAC).

## Configuraci&oacute;n

Define la cuenta de Azure Storage antes de subir evidencias (ver `application.properties`):

```
AZURE_STORAGE_ACCOUNT_NAME=<nombre-de-tu-cuenta>
```

La autenticaci&oacute;n usa la cadena de credenciales por defecto de Azure Identity (`az login`,
variables `AZURE_CLIENT_ID`/`AZURE_CLIENT_SECRET`/`AZURE_TENANT_ID`, Managed Identity, etc.).

## Ejecutar

```powershell
..\mvnw.cmd -f .\pom.xml test
..\mvnw.cmd -f .\pom.xml spring-boot:run
```

## Endpoints

- `POST /api/v1/transactions`
- `GET /api/v1/transactions/{transactionId}`
- `POST /api/v1/transactions/{transactionId}/evidencias` (multipart, campo `file`)

## Respuesta

- `202 Accepted` cuando la transacci&oacute;n es nueva
- `200 OK` cuando el `transactionId` ya existe
- `201 Created` cuando la evidencia se sube correctamente
- `400 Bad Request` para validaciones (incluye tama&ntilde;o/tipo de archivo inv&aacute;lido)
- `404 Not Found` para consultas inexistentes o evidencias de transacciones que no existen
