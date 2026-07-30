import { useEffect, useMemo, useState } from 'react';
import StatCard from '../components/StatCard';
import TransactionTable from '../components/TransactionTable';
import TransactionDetailCard from '../components/TransactionDetailCard';
import { downloadEvidence, getAllTransactionScores, getErrorMessage, getTransaction, getTransactionEvidenceList, getTransactionRisk, getTransactions, updateTransactionStatus } from '../services/api';

function DashboardPage() {
  const [transactions, setTransactions] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [selectedTransaction, setSelectedTransaction] = useState(null);
  const [risk, setRisk] = useState(null);
  const [risksById, setRisksById] = useState({});
  const [evidences, setEvidences] = useState([]);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [statusSaving, setStatusSaving] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    async function loadTransactions() {
      try {
        setLoading(true);
        const data = await getTransactions();
        const list = Array.isArray(data) ? data : [];
        setTransactions(list);
        if (list.length) {
          setSelectedId(list[0].transactionId);
        }

        const scoresResult = await getAllTransactionScores();
        const nextRisks = {};
        (Array.isArray(scoresResult) ? scoresResult : []).forEach((score) => {
          nextRisks[score.transactionId] = score;
        });
        setRisksById((prev) => ({ ...prev, ...nextRisks }));
      } catch (err) {
        setError(getErrorMessage(err));
      } finally {
        setLoading(false);
      }
    }

    loadTransactions();
  }, []);

  useEffect(() => {
    if (!selectedId) {
      setSelectedTransaction(null);
      setRisk(null);
      setEvidences([]);
      return;
    }

    async function loadDetails() {
      try {
        setDetailLoading(true);
        const [transaction, riskResult, evidenceResult] = await Promise.all([
          getTransaction(selectedId),
          getTransactionRisk(selectedId),
          getTransactionEvidenceList(selectedId)
        ]);

        setSelectedTransaction(transaction);
        setRisk(riskResult);
        setRisksById((prev) => ({ ...prev, [selectedId]: riskResult }));
        setEvidences(Array.isArray(evidenceResult) ? evidenceResult : []);
      } catch (err) {
        setSelectedTransaction(null);
        setRisk(null);
        setEvidences([]);
        setError(getErrorMessage(err));
      } finally {
        setDetailLoading(false);
      }
    }

    loadDetails();
  }, [selectedId]);

  async function handleStatusSave(status) {
    try {
      setStatusSaving(true);
      const updatedRisk = await updateTransactionStatus(selectedId, status);
      setRisk(updatedRisk);
      setRisksById((prev) => ({ ...prev, [selectedId]: updatedRisk }));
    } catch (err) {
      setError(getErrorMessage(err));
      throw err;
    } finally {
      setStatusSaving(false);
    }
  }

  const summary = useMemo(() => ({
    total: transactions.length,
    alerts: transactions.filter((tx) => {
      const score = Number(risksById[tx.transactionId]?.score ?? 0);
      const threshold = Number(risksById[tx.transactionId]?.threshold ?? 0);
      return score > threshold;
    }).length,
    latest: transactions[0]?.transactionId || '—'
  }), [transactions, risksById]);

  async function handlePreviewEvidence(blobName) {
    try {
      const { url } = await downloadEvidence(selectedId, blobName);
      window.open(url, '_blank', 'noopener,noreferrer');
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  return (
    <div className="page-stack">
      <section className="hero-card">
        <div>
          <p className="eyebrow">Vista general</p>
          <h2>Centro de ingestión y scoring antifraude</h2>
          <p>Visualiza transacciones recientes con datos protegidos y selecciona IDs desde una lista controlada.</p>
        </div>
      </section>

      <section className="stats-grid">
        <StatCard title="Transacciones" value={summary.total} accent="accent-blue" />
        <StatCard title="Alertas elevadas" value={summary.alerts} accent="accent-red" />
        <StatCard title="Última ingestión" value={summary.latest} accent="accent-purple" />
      </section>

      <section className="content-grid">
        <TransactionTable
          transactions={transactions}
          selectedId={selectedId}
          loading={loading}
          error={error}
          onSelect={setSelectedId}
          risksById={risksById}
        />

        {detailLoading ? <div className="panel">Cargando detalle...</div> : (
          <TransactionDetailCard
            transaction={selectedTransaction}
            risk={risk}
            evidences={evidences}
            onPreview={handlePreviewEvidence}
            onStatusSave={handleStatusSave}
            statusSaving={statusSaving}
          />
        )}
      </section>
    </div>
  );
}

export default DashboardPage;
