import { Link, useLocation, useParams } from 'react-router-dom';
import { formatCurrency, formatDateTime, maskIdentifier } from '../utils/formatters';
import ScoreBar from '../components/ScoreBar';

function FraudCaseDetailPage() {
  const { caseId } = useParams();
  const location = useLocation();
  const { transaction, risk, evidences = [] } = location.state || {};

  const fraudCase = risk?.fraudCase;
  const score = risk?.score ?? 0;
  const activations = Array.isArray(risk?.activations) ? risk.activations : [];

  if (!transaction || !fraudCase) {
    return (
      <div className="page-stack">
        <section className="hero-card">
          <div>
            <p className="eyebrow">Caso</p>
            <h2>{maskIdentifier(caseId)}</h2>
            <p>No se encontraron datos del caso para mostrar.</p>
          </div>
        </section>
        <div className="panel">
          <Link to="/">Volver al panel</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="page-stack">
      <section className="hero-card">
        <div>
          <p className="eyebrow">Caso de fraude</p>
          <h2>{maskIdentifier(fraudCase.caseId)}</h2>
          <p>Detalle del caso asociado a la transacción {maskIdentifier(transaction.transactionId)}.</p>
        </div>
      </section>

      <div className="panel detail-panel enhanced-panel">
        <div className="panel-header">
          <div>
            <p className="eyebrow">Detalle</p>
            <h2>{maskIdentifier(transaction.transactionId)}</h2>
            <div className="muted small">Estado: {fraudCase.status}</div>
          </div>
          <div className="detail-actions">
            <Link to={`/transactions/${transaction.transactionId}`}>Ver transacción</Link>
          </div>
        </div>

        <div className="detail-body">
          <div className="detail-left">
            <div className="detail-grid">
              <div>
                <strong>Monto</strong>
                <p>{transaction.amount != null ? `${transaction.amount} ${transaction.currency}` : '—'}</p>
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
                <strong>Comercio</strong>
                <p>{maskIdentifier(transaction.merchantId)} / {transaction.merchantCategory}</p>
              </div>
            </div>

            <div className="panel-section">
              <h3>Evidencias</h3>
              <div className="chip-list">
                {(evidences || []).map((name) => (
                  <span key={name} className="chip-inline">{name}</span>
                ))}
              </div>
            </div>
          </div>

          <aside className="detail-right">
            <div className="scoring-card">
              <h3>Riesgo</h3>
              <ScoreBar score={score} />
              <p className="muted small">{risk?.scored ? `Evaluado • ${activations.length} reglas` : 'Pendiente de evaluación'}</p>

              <div className="panel-section status-section">
                <h4>Información del caso</h4>
                <p className="small">Caso ID: {maskIdentifier(fraudCase.caseId)}</p>
                <p className="small">Estado: {fraudCase.status}</p>
                <p className="small">Abierto: {formatDateTime(fraudCase.openedAt)}</p>
                <p className="small">Puntuación: {score}</p>
              </div>

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
    </div>
  );
}

export default FraudCaseDetailPage;
