export function formatCurrency(amount, currency = 'COP') {
  const value = Number(amount);
  if (!Number.isFinite(value)) return '-';
  return new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency,
    maximumFractionDigits: 2
  }).format(value);
}

export function formatDateTime(value) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('es-CO', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  });
}

export function maskIdentifier(value) {
  if (!value) return '—';
  const text = String(value);
  if (text.length <= 4) return `***${text.slice(-2)}`;
  return `${text.slice(0, 2)}***${text.slice(-2)}`;
}

export function getScoreClass(score) {
  if (score === null || score === undefined) return 'neutral';
  if (score >= 60) return 'high';
  if (score >= 30) return 'medium';
  return 'low';
}
