import { formatCurrency, formatDateTime, maskIdentifier } from '../utils/formatters';
import { useNavigate } from 'react-router-dom';

function TransactionTable({ transactions, selectedId, loading, error, onSelect }) {
  const navigate = useNavigate();

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
              <th>Comercio</th>
            </tr>
          </thead>
          <tbody>
            {transactions.map((tx) => (
              <tr key={tx.transactionId} className={selectedId === tx.transactionId ? 'selected' : ''}>
                <td>
                  <button
                    className="link-button"
                    onClick={() => {
                      onSelect?.(tx.transactionId);
                      navigate(`/transactions/${tx.transactionId}`);
                    }}
                  >
                    {maskIdentifier(tx.transactionId)}
                  </button>
                </td>
                <td>{maskIdentifier(tx.accountId)}</td>
                <td>{formatCurrency(tx.amount, tx.currency)}</td>
                <td>{formatDateTime(tx.ingestedAt || tx.occurredAt)}</td>
                <td>{tx.merchantCategory}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default TransactionTable;
