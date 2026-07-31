#!/usr/bin/env bash
#
# Foto rapida de la infraestructura implicada, antes o despues de una prueba.
#
#   bash test-sh/05-estado.sh

source "$(dirname "$0")/comun.sh"
comprobar_sesion

titulo "Colas en $STORAGE_ACCOUNT"
for cola in "$QUEUE" "$QUEUE_POISON"; do
  visibles=$(az storage message peek --queue-name "$cola" --account-name "$STORAGE_ACCOUNT" \
             --auth-mode login --num-messages 32 --query "length(@)" -o tsv 2>/dev/null || echo "?")
  printf '  %-26s %s mensaje(s) visibles\n' "$cola" "$visibles"
done
echo "  (0 en la principal es lo normal: la Function consume en segundos)"

titulo "Servicios"
printf '  %-12s %s\n' "Function" \
  "$(az functionapp show --name "$FUNCTION_APP" -g "$RG" --query state -o tsv 2>/dev/null)"
printf '  %-12s %s\n' "App Service" \
  "$(az webapp show --name "$APP_SERVICE" -g "$RG" --query state -o tsv 2>/dev/null)"
printf '  %-12s %s\n' "Trigger" \
  "$(az functionapp function list --name "$FUNCTION_APP" -g "$RG" \
     --query "[0].{n:name,d:isDisabled}" -o tsv 2>/dev/null)"

titulo "Configuracion de la Function"
az functionapp config appsettings list --name "$FUNCTION_APP" -g "$RG" \
  --query "[?name=='FraudQueueName'||name=='FraudQueueStorage__queueServiceUri'||name=='FraudAlertRecipients'||name=='EmailSenderAddress'].{ajuste:name,valor:value}" \
  -o tsv 2>/dev/null | sed 's/^/  /'

titulo "Permiso del App Service sobre la cola"
principal=$(az webapp identity show --name "$APP_SERVICE" -g "$RG" --query principalId -o tsv 2>/dev/null)
alcance=$(az storage account show --name "$STORAGE_ACCOUNT" -g "$RG" --query id -o tsv 2>/dev/null)
roles=$(az role assignment list --assignee "$principal" --scope "$alcance" \
        --query "[].roleDefinitionName" -o tsv 2>/dev/null)
echo "  ${roles:-  SIN ROL: el App Service no podra encolar}"
