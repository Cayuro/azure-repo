import { formatCurrency, formatDateTime, maskIdentifier } from '../utils/formatters';

function TransactionTable({ transactions, selectedId, loading, error, onSelect, risksById = {} }) {
  function getRiskClass(score) {
    const value = Number(score) || 0;
    if (value >= 70) return 'score-high';
    if (value >= 40) return 'score-medium';
    return 'score-low';
  }

  function getRiskLabel(score) {
    const value = Number(score) || 0;
    if (value >= 70) return 'ALTO';
    if (value >= 40) return 'MEDIO';
    return 'BAJO';
  }

  if (loading) {
    return <div className="panel loading">Cargando transacciones...</div>;
  }

  if (error) {
    return <div className="panel error">{error}</div>;
  }

  return (
    <div className="panel">
      <div className="panel-header">
        <div>
          <p className="eyebrow">Monitor</p>
          <h2>Transacciones recientes</h2>
        </div>
        <span className="pill">{transactions.length} registros</span>
      </div>

      <div className="table-shell">
        <table className="data-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Cuenta</th>
              <th>Monto</th>
              <th>Fecha</th>
              <th>Scoring</th>
              <th>Estado</th>
              <th>Comercio</th>
            </tr>
          </thead>
          <tbody>
            {transactions.map((tx) => {
              const risk = risksById?.[tx.transactionId];
              const score = risk?.score ?? null;
              const status = risk?.fraudCase?.status || '—';
              const riskClass = score == null ? 'neutral' : getRiskClass(score);
              const riskLabel = score == null ? '—' : getRiskLabel(score);

              return (
                <tr
                  key={tx.transactionId}
                  className={selectedId === tx.transactionId ? 'selected' : ''}
                  onClick={() => onSelect?.(tx.transactionId)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter' || event.key === ' ') {
                      event.preventDefault();
                      onSelect?.(tx.transactionId);
                    }
                  }}
                  tabIndex={0}
                >
                  <td>
                    <button
                      type="button"
                      className="link-button"
                      onClick={(event) => {
                        event.stopPropagation();
                        onSelect?.(tx.transactionId);
                      }}
                    >
                      {maskIdentifier(tx.transactionId)}
                    </button>
                  </td>
                  <td>{maskIdentifier(tx.accountId)}</td>
                  <td>{formatCurrency(tx.amount, tx.currency)}</td>
                  <td>{formatDateTime(tx.ingestedAt || tx.occurredAt)}</td>
                  <td>
                    <span className={`transaction-score-pill ${riskClass}`}>
                      <span className="score-value">{score == null ? '—' : Math.round(Number(score))}</span>
                      <span className="score-label">{riskLabel}</span>
                    </span>
                  </td>
                  <td>{status}</td>
                  <td>{tx.merchantCategory}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default TransactionTable;
