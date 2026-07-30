import React from 'react';

function ScoreBar({ score = 0, size = 160 }) {
  const pct = Math.max(0, Math.min(100, Number(score) || 0));
  const level = pct >= 70 ? 'high' : pct >= 40 ? 'medium' : 'low';
  const label = level === 'high' ? 'ALTO' : level === 'medium' ? 'MEDIO' : 'BAJO';

  // SVG gauge params
  const stroke = Math.max(10, Math.round(size * 0.08));
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  const halfCirc = circumference / 2; // semicircle length
  const dashArray = `${halfCirc} ${circumference}`;
  const dashOffset = halfCirc * (1 - pct / 100);

  const levelColors = {
    high: '#fb7185',
    medium: '#f59e0b',
    low: '#34d399',
  };

  return (
    <div className={`score-wrapper enhanced score-${level}`} style={{ maxWidth: `${size}px` }}>
      <div className="score-gauge">
        <svg className="gauge-svg" width={size} height={Math.round(size / 2)} viewBox={`0 0 ${size} ${size / 2}`}>
          <g transform={`translate(${size / 2}, ${size / 2})`}>
            <circle
              className="gauge-track"
              r={radius}
              cx="0"
              cy="0"
              fill="none"
              stroke="rgba(255,255,255,0.06)"
              strokeWidth={stroke}
              strokeLinecap="round"
              strokeDasharray={dashArray}
              strokeDashoffset={halfCirc}
              transform={`rotate(-180)`}
            />

            <circle
              className="gauge-fill"
              r={radius}
              cx="0"
              cy="0"
              fill="none"
              stroke={levelColors[level]}
              strokeWidth={stroke}
              strokeLinecap="round"
              strokeDasharray={dashArray}
              strokeDashoffset={dashOffset}
              transform={`rotate(-180)`}
            />
          </g>
        </svg>

        <div className="gauge-label">
          <div className="score-number">{Math.round(pct)}</div>
          <div className="score-pill">{label}</div>
        </div>
      </div>
    </div>
  );
}

export default ScoreBar;
