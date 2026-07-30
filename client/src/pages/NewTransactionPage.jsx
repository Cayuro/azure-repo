import TransactionForm from '../components/TransactionForm';

function NewTransactionPage() {
  return (
    <div className="page-stack">
      <section className="hero-card">
        <div>
          <p className="eyebrow">Ingresar</p>
          <h2>Nueva transacción</h2>
          <p>Envia una operación usando identificadores pre-cargados desde una lista controlada.</p>
        </div>
      </section>
      <TransactionForm />
    </div>
  );
}

export default NewTransactionPage;
