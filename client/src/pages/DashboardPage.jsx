import { useEffect, useMemo, useState } from 'react';
import StatCard from '../components/StatCard';
import TransactionTable from '../components/TransactionTable';
import TransactionDetailCard from '../components/TransactionDetailCard';
import { downloadEvidence, getErrorMessage, getTransaction, getTransactionEvidenceList, getTransactionRisk, getTransactions } from '../services/api';

function DashboardPage() {
  const [transactions, setTransactions] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [selectedTransaction, setSelectedTransaction] = useState(null);
  const [risk, setRisk] = useState(null);
  const [evidences, setEvidences] = useState([]);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    async function loadTransactions() {
      try {
        setLoading(true);
        const data = await getTransactions();
        setTransactions(Array.isArray(data) ? data : []);
        if (data?.length) {
          setSelectedId(data[0].transactionId);
        }
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

  const summary = useMemo(() => ({
    total: transactions.length,
    alerts: transactions.filter((tx) => Number(tx.amount) > 2500).length,
    latest: transactions[0]?.transactionId || '—'
  }), [transactions]);

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
        />

        {detailLoading ? <div className="panel">Cargando detalle...</div> : (
          <TransactionDetailCard
            transaction={selectedTransaction}
            risk={risk}
            evidences={evidences}
            onPreview={handlePreviewEvidence}
          />
        )}
      </section>
    </div>
  );
}

export default DashboardPage;
