import { useEffect, useState } from 'react';
import { formatCurrency, formatDateTime, maskIdentifier } from '../utils/formatters';
import ScoreBar from './ScoreBar';
import EvidenceUploader from './EvidenceUploader';

const STATUS_OPTIONS = [
  { value: 'ABIERTO', label: 'Abierto' },
  { value: 'EN_REVISION', label: 'En revisión' },
  { value: 'REVISADO', label: 'Revisado' },
  { value: 'CERRADO', label: 'Cerrado' },
  { value: 'PENDIENTE', label: 'Pendiente' }
];

function TransactionDetailCard({ transaction, risk, evidences, onPreview, onStatusSave, onUploadSuccess, statusSaving, statusError }) {
  const [selectedStatus, setSelectedStatus] = useState('');

  useEffect(() => {
    setSelectedStatus(risk?.fraudCase?.status || '');
  }, [risk?.fraudCase?.status]);

  if (!transaction) return <div className="panel">Selecciona una transacción para ver detalles.</div>;

  const score = risk?.score ?? 0;
  const activations = Array.isArray(risk?.activations) ? risk.activations : [];
  const fraudCase = risk?.fraudCase;
  const hasFraudCase = Boolean(fraudCase && fraudCase.caseId);

  const handleSaveStatus = async () => {
    if (!onStatusSave || !selectedStatus || selectedStatus === fraudCase?.status) return;
    await onStatusSave(selectedStatus);
  };

  return (
    <div className="panel detail-panel enhanced-panel">
      <div className="panel-header">
        <div>
          <p className="eyebrow">Detalle</p>
          <h2>{maskIdentifier(transaction.transactionId)}</h2>
          <div className="muted small">{transaction.merchantCategory} · {maskIdentifier(transaction.accountId)}</div>
          <div className="muted small">Estado caso: {fraudCase?.status ?? 'No aplica'}</div>
        </div>
        <div className="detail-actions">
          {hasFraudCase ? (
            <button className="primary-button" onClick={handleSaveStatus} disabled={statusSaving || !selectedStatus || selectedStatus === fraudCase.status}>
              {statusSaving ? 'Guardando...' : 'Guardar estado'}
            </button>
          ) : null}
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

          {transaction?.transactionId ? (
            <EvidenceUploader transactionId={transaction.transactionId} onUploadSuccess={onUploadSuccess} />
          ) : null}
        </div>

        <aside className="detail-right">
          <div className="scoring-card">
            <h3>Riesgo</h3>
            <ScoreBar score={score} />
            <p className="muted small">{risk?.scored ? `Evaluado • ${activations.length} reglas` : 'Pendiente de evaluación'}</p>

            {hasFraudCase ? (
              <div className="panel-section status-section">
                <h4>Estado del caso</h4>
                <p className="small">Caso ID: {maskIdentifier(fraudCase.caseId)}</p>
                <p className="small">Abierto: {formatDateTime(fraudCase.openedAt)}</p>
                <label>
                  <span>Status</span>
                  <select
                    value={selectedStatus}
                    onChange={(event) => setSelectedStatus(event.target.value)}
                    disabled={!hasFraudCase}
                  >
                    <option value="">Selecciona un estado</option>
                    {STATUS_OPTIONS.map((option) => (
                      <option key={option.value} value={option.value}>{option.label}</option>
                    ))}
                  </select>
                </label>
                {statusError ? <div className="panel-message error">{statusError}</div> : null}
              </div>
            ) : null}

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
