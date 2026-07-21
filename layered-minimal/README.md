# Ingesta layered minimal

Version simple de la API de ingesta con arquitectura layered y una interfaz web ligera para probar el flujo de negocio.

## Arquitectura

- controller
- service
- repository
- dto
- exception

Las transacciones se guardan en memoria. La carga de evidencias usa Azure Blob Storage sin claves, mediante `DefaultAzureCredentialBuilder` y RBAC.

## Requisitos

- Java 11
- Maven 3.8+
- Azure CLI autenticado si vas a usar Blob Storage

## Configuracion

Define la cuenta de Azure Storage antes de subir evidencias (ver `application.properties`):

```properties
azure.storage.account-name=<nombre-de-tu-cuenta>
azure.storage.container-name=evidencias-financieras-privado
```

La autenticacion usa la cadena de credenciales por defecto de Azure Identity (`az login`, Managed Identity, variables `AZURE_CLIENT_ID`/`AZURE_CLIENT_SECRET`/`AZURE_TENANT_ID`, etc.).

## Ejecutar localmente

```powershell
mvn test
mvn spring-boot:run
```

Luego abre:

- http://localhost:8080/ para la interfaz web
- http://localhost:8080/api/v1/transactions para la API

## UI web

La aplicacion incluye una pequeña interfaz con Thymeleaf para:

- crear transacciones
- consultar transacciones por ID
- ver mensajes de resultado

## Endpoints

- `POST /api/v1/transactions`
- `GET /api/v1/transactions/{transactionId}`
- `POST /api/v1/transactions/{transactionId}/evidencias` (multipart, campo `file`)

## Respuestas

- `202 Accepted` cuando la transaccion es nueva
- `200 OK` cuando el `transactionId` ya existe
- `201 Created` cuando la evidencia se sube correctamente
- `400 Bad Request` para validaciones
- `404 Not Found` para consultas inexistentes o evidencias de transacciones que no existen

## Pruebas

Se incluyen pruebas de integración para:

- flujo de API de transacciones
- renderizado de la UI web
- creación y consulta desde el controlador web

Ejecutar:

```powershell
mvn test
```

## Despliegue en Azure App Service

El proyecto ya esta preparado para despliegue en App Service con Java 11. El artefacto generado es:

- `target/ingesta-layered-minimal-0.0.1-SNAPSHOT.jar`

Para la configuracion de runtime, se incluye `system.properties` con:

```properties
java.runtime.version=11
```
