function StatCard({ title, value, accent }) {
  return (
    <div className={`stat-card ${accent || ''} modern-stat`}>
      <div className="stat-icon" aria-hidden>📊</div>
      <div>
        <p>{title}</p>
        <strong>{value}</strong>
      </div>
    </div>
  );
}

export default StatCard;
