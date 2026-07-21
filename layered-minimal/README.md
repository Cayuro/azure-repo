# Ingesta layered minimal

Versi&oacute;n simple de la API de ingesta para entender el flujo con arquitectura layered:

- controller
- service
- repository
- dto
- exception

No usa Azure, no usa capas extra y guarda en memoria.

## Ejecutar

```powershell
..\mvnw.cmd -f .\pom.xml test
..\mvnw.cmd -f .\pom.xml spring-boot:run
```

## Endpoints

- `POST /api/v1/transactions`
- `GET /api/v1/transactions/{transactionId}`

## Respuesta

- `202 Accepted` cuando la transacci&oacute;n es nueva
- `200 OK` cuando el `transactionId` ya existe
- `400 Bad Request` para validaciones
- `404 Not Found` para consultas inexistentes
