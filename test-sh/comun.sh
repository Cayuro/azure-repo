#!/usr/bin/env bash
#
# Configuracion compartida. No se ejecuta directo: los demas scripts hacen "source" de este.

set -euo pipefail

RG=rg-centinela-prod

# Cuenta y colas del emailSender. OJO: NO es sttransaccionesfase1ch1 (esa es la de
# ingesta y evidencias). Las dos cuentas tienen una cola llamada cola-casos-fraude,
# pero la que tiene consumidor real es esta.
STORAGE_ACCOUNT=stcolafraudecentinela
QUEUE=cola-casos-fraude
QUEUE_POISON=cola-casos-fraude-poison

FUNCTION_APP=func-email-sender-centinela
APP_SERVICE=appcentinelaprodgrupo3

# Application Insights de la Function (no el de la app de scoring).
APPINSIGHTS_ID=830836a3-3c82-48d3-9353-2030e32c755a

# Contra el App Service desplegado en vez de local:
#   API=http://appcentinelaprodgrupo3.azurewebsites.net bash test-sh/02-enviar-transacciones.sh
API="${API:-http://localhost:8080}"

export AZURE_CORE_ONLY_SHOW_ERRORS=true

titulo() { printf '\n\033[1m== %s ==\033[0m\n' "$1"; }

comprobar_sesion() {
  az account show --query id -o tsv >/dev/null 2>&1 || {
    echo "No hay sesion de Azure. Ejecuta:  az login" >&2
    exit 1
  }
}
