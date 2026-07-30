import { BrowserRouter, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import DashboardPage from './pages/DashboardPage';
import FraudCaseDetailPage from './pages/FraudCaseDetailPage';
import NewTransactionPage from './pages/NewTransactionPage';
import TransactionDetailPage from './pages/TransactionDetailPage';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/transactions/new" element={<NewTransactionPage />} />
          <Route path="/transactions/:transactionId" element={<TransactionDetailPage />} />
          <Route path="/cases/:caseId" element={<FraudCaseDetailPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
