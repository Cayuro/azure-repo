import { useEffect, useState, useCallback } from 'react';
import { useParams } from 'react-router-dom';
import TransactionDetailCard from '../components/TransactionDetailCard';
import EvidenceUploader from '../components/EvidenceUploader';
import { downloadEvidence, getErrorMessage, getTransaction, getTransactionEvidenceList, getTransactionRisk, updateTransactionStatus } from '../services/api';

function TransactionDetailPage() {
  const { transactionId } = useParams();
  const [transaction, setTransaction] = useState(null);
  const [risk, setRisk] = useState(null);
  const [evidences, setEvidences] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [statusSaving, setStatusSaving] = useState(false);
  const [statusError, setStatusError] = useState(null);

  useEffect(() => {
    async function load() {
      try {
        setLoading(true);
        const [detail, riskResult, evidenceResult] = await Promise.all([
          getTransaction(transactionId),
          getTransactionRisk(transactionId),
          getTransactionEvidenceList(transactionId)
        ]);
        setTransaction(detail);
        setRisk(riskResult);
        setEvidences(Array.isArray(evidenceResult) ? evidenceResult : []);
      } catch (err) {
        setError(getErrorMessage(err));
      } finally {
        setLoading(false);
      }
    }

    if (transactionId) {
      load();
    }
  }, [transactionId]);

  const handleUploadSuccess = useCallback(async () => {
    try {
      const evidenceResult = await getTransactionEvidenceList(transactionId);
      setEvidences(Array.isArray(evidenceResult) ? evidenceResult : []);
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }, [transactionId]);

  async function handleStatusSave(status) {
    try {
      setStatusError(null);
      setStatusSaving(true);
      const updatedRisk = await updateTransactionStatus(transactionId, status);
      setRisk(updatedRisk);
    } catch (err) {
      const message = getErrorMessage(err);
      setStatusError(message);
      throw err;
    } finally {
      setStatusSaving(false);
    }
  }

  async function handlePreviewEvidence(blobName) {
    try {
      const { url } = await downloadEvidence(transactionId, blobName);
      window.open(url, '_blank', 'noopener,noreferrer');
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  return (
    <div className="page-stack">
      <section className="hero-card">
        <div>
          <p className="eyebrow">Detalle</p>
          <h2>{transactionId}</h2>
          <p>Revisa la información con enfoque de seguridad y exposición mínima.</p>
        </div>
      </section>

      {loading ? <div className="panel">Cargando transacción...</div> : null}
      {error ? <div className="panel error">{error}</div> : null}
      {!loading && !error ? (
        <>
          <TransactionDetailCard
            transaction={transaction}
            risk={risk}
            evidences={evidences}
            onPreview={handlePreviewEvidence}
            onStatusSave={handleStatusSave}
            statusSaving={statusSaving}
            statusError={statusError}
          />
          <EvidenceUploader transactionId={transactionId} onUploadSuccess={handleUploadSuccess} />
        </>
      ) : null}
    </div>
  );
}

export default TransactionDetailPage;
