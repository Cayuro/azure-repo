const API_BASE = '/api/v1/transactions';

async function parseResponse(response) {
  let body = null;
  try {
    body = await response.json();
  } catch {
    body = null;
  }

  if (!response.ok) {
    const message = body?.message || body?.error || response.statusText || 'Error del servidor';
    throw new Error(message);
  }

  return body;
}

export async function getTransactions() {
  const response = await fetch(API_BASE);
  return parseResponse(response);
}

export async function getTransaction(transactionId) {
  const response = await fetch(`${API_BASE}/${encodeURIComponent(transactionId)}`);
  return parseResponse(response);
}

export async function createTransaction(payload) {
  const response = await fetch(API_BASE, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });
  return parseResponse(response);
}

export async function getTransactionRisk(transactionId) {
  const response = await fetch(`${API_BASE}/${encodeURIComponent(transactionId)}/riesgo`);
  return parseResponse(response);
}

export async function getAllTransactionScores() {
  const response = await fetch(`${API_BASE}/riesgos`);
  return parseResponse(response);
}

export async function getTransactionEvidenceList(transactionId) {
  const response = await fetch(`${API_BASE}/${encodeURIComponent(transactionId)}/evidencias`);
  return parseResponse(response);
}

export async function downloadEvidence(transactionId, blobName) {
  const response = await fetch(`${API_BASE}/${encodeURIComponent(transactionId)}/evidencias/${encodeURIComponent(blobName)}`);
  if (!response.ok) {
    throw new Error((await parseResponse(response)).message || 'No se pudo descargar la evidencia');
  }
  const blob = await response.blob();
  return {
    url: URL.createObjectURL(blob),
    type: blob.type
  };
}

export async function uploadEvidence(transactionId, file) {
  const formData = new FormData();
  formData.append('file', file);

  const response = await fetch(`${API_BASE}/${encodeURIComponent(transactionId)}/evidencias`, {
    method: 'POST',
    body: formData
  });
  return parseResponse(response);
}

export function getErrorMessage(error) {
  if (!error) return 'Ha ocurrido un error inesperado.';
  if (error.status === 429) return 'Se ha excedido el límite de peticiones.';
  return error.message || 'Ha ocurrido un error inesperado.';
}
