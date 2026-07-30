import { formatCurrency, formatDateTime, maskIdentifier } from '../utils/formatters';
import ScoreBar from './ScoreBar';

function TransactionDetailCard({ transaction, risk, evidences, onPreview }) {
  if (!transaction) return <div className="panel">Selecciona una transacción para ver detalles.</div>;

  const score = risk?.score ?? 0;
  const activations = Array.isArray(risk?.activations) ? risk.activations : [];

  return (
    <div className="panel detail-panel enhanced-panel">
      <div className="panel-header">
        <div>
          <p className="eyebrow">Detalle</p>
          <h2>{maskIdentifier(transaction.transactionId)}</h2>
          <div className="muted small">{transaction.merchantCategory} · {maskIdentifier(transaction.accountId)}</div>
        </div>
        <div className="detail-actions">
          <button className="primary-button">Marcar como revisado</button>
        </div>
      </div>

      <div className="detail-body">
        <div className="detail-left">
          <div className="detail-grid">
            <div>
              <strong>Cuenta</strong>
              <p>{maskIdentifier(transaction.accountId)}</p>
            </div>
            <div>
              <strong>Monto</strong>
              <p>{formatCurrency(transaction.amount, transaction.currency)}</p>
            </div>
            <div>
              <strong>Ocurrió</strong>
              <p>{formatDateTime(transaction.occurredAt)}</p>
            </div>
            <div>
              <strong>Ingestada</strong>
              <p>{formatDateTime(transaction.ingestedAt)}</p>
            </div>
            <div>
              <strong>Ubicación</strong>
              <p>Coordenadas protegidas</p>
            </div>
            <div>
              <strong>Comercio</strong>
              <p>{maskIdentifier(transaction.merchantId)} / {transaction.merchantCategory}</p>
            </div>
          </div>

          <div className="panel-section evidences">
            <h3>Evidencias</h3>
            <div className="chip-list">
              {(evidences || []).map((name) => (
                <button key={name} className="chip-inline" onClick={() => onPreview?.(name)}>
                  {name}
                </button>
              ))}
            </div>
          </div>
        </div>

        <aside className="detail-right">
          <div className="scoring-card">
            <h3>Riesgo</h3>
            <ScoreBar score={score} />
            <p className="muted small">{risk?.scored ? `Evaluado • ${activations.length} reglas` : 'Pendiente de evaluación'}</p>

            {activations.length > 0 && (
              <div className="rule-list">
                {activations.map((a) => (
                  <div key={a.ruleId} className="rule-item" title={`Regla ${a.ruleId}`}>
                    <div className="rule-id">{a.ruleId}</div>
                    <div className="rule-points">+{a.points}</div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </aside>
      </div>
    </div>
  );
}

export default TransactionDetailCard;
