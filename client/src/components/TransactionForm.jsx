import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { createTransaction, getErrorMessage } from '../services/api';

const initialState = {
  accountId: 'ACC-1001',
  amount: '2500',
  currency: 'COP',
  latitude: '4.7110',
  longitude: '-74.0721',
  merchantId: 'MER-1001',
  merchantCategory: 'GAMBLING'
};

const accountOptions = ['ACC-1001', 'ACC-1002', 'ACC-1003', 'ACC-1004'];
const merchantOptions = ['MER-1001', 'MER-1002', 'MER-1003', 'MER-1004'];
const merchantCategories = ['GAMBLING', 'CRYPTO', 'ADULT', 'RETAIL', 'TRAVEL'];

function TransactionForm() {
  const navigate = useNavigate();
  const [form, setForm] = useState(initialState);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState(null);
  const [error, setError] = useState(null);

  const helperText = useMemo(() => 'El identificador de transacción se genera automáticamente en el backend.', []);

  function handleChange(event) {
    const { name, value } = event.target;
    setForm((prev) => ({ ...prev, [name]: value }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setLoading(true);
    setError(null);
    setMessage(null);

    try {
      const payload = {
        accountId: form.accountId,
        amount: Number(form.amount),
        currency: form.currency,
        latitude: Number(form.latitude),
        longitude: Number(form.longitude),
        merchantId: form.merchantId,
        merchantCategory: form.merchantCategory
      };

      const result = await createTransaction(payload);
      setMessage(`Transacción ${result.transactionId} enviada con estado ${result.status}.`);
      setForm(initialState);
      navigate(`/transactions/${result.transactionId}`);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <form className="panel form-panel" onSubmit={handleSubmit}>
      <div className="panel-header">
        <div>
          <p className="eyebrow">Ingreso</p>
          <h2>Crear transacción</h2>
        </div>
      </div>
      <p className="helper-text">{helperText}</p>

      <div className="form-grid">
        <label>
          accountId
          <select name="accountId" value={form.accountId} onChange={handleChange}>
            {accountOptions.map((option) => <option key={option} value={option}>{option}</option>)}
          </select>
        </label>
        <label>
          amount
          <input name="amount" type="number" step="0.01" min="0.01" value={form.amount} onChange={handleChange} required />
        </label>
        <label>
          currency
          <select name="currency" value={form.currency} onChange={handleChange}>
            <option value="COP">COP</option>
            <option value="USD">USD</option>
            <option value="EUR">EUR</option>
          </select>
        </label>
        <label>
          latitude
          <input name="latitude" type="number" step="0.0001" value={form.latitude} onChange={handleChange} required />
        </label>
        <label>
          longitude
          <input name="longitude" type="number" step="0.0001" value={form.longitude} onChange={handleChange} required />
        </label>
        <label>
          merchantId
          <select name="merchantId" value={form.merchantId} onChange={handleChange}>
            {merchantOptions.map((option) => <option key={option} value={option}>{option}</option>)}
          </select>
        </label>
        <label>
          merchantCategory
          <select name="merchantCategory" value={form.merchantCategory} onChange={handleChange}>
            {merchantCategories.map((option) => <option key={option} value={option}>{option}</option>)}
          </select>
        </label>
      </div>

      {error ? <div className="message error">{error}</div> : null}
      {message ? <div className="message success">{message}</div> : null}

      <button className="primary-button" type="submit" disabled={loading}>
        {loading ? 'Enviando...' : 'Enviar transacción'}
      </button>
    </form>
  );
}

export default TransactionForm;
