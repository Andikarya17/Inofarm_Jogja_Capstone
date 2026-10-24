import React, { useState } from 'react';

type ChartMetric = 'suhu' | 'ph' | 'keduanya';

interface SensorChartProps {
  onMoreOptions?: () => void;
}

export const SensorChart: React.FC<SensorChartProps> = ({ onMoreOptions }) => {
  const [metric, setMetric] = useState<ChartMetric>('suhu');
  const [hoverIndex, setHoverIndex] = useState<number | null>(null);

  // Time data points
  const timeLabels = ['04:00', '05:00', '06:00', '07:00', '08:00'];

  // Sensor series values for the 5 timestamps
  const suhuValues = [23.8, 24.1, 24.0, 24.6, 24.5]; // °C
  const phValues = [6.0, 6.1, 6.3, 6.1, 6.2];         // pH

  // Normalized coordinate points (viewBox 0 0 400 120)
  // Suhu Air path: (0, 95) -> (100, 50) -> (200, 75) -> (300, 20) -> (400, 35)
  const suhuPathD = 'M 0 95 C 50 80, 70 50, 100 50 C 130 50, 160 85, 200 75 C 240 65, 270 20, 300 20 C 330 20, 370 25, 400 35';
  const suhuFillD = `${suhuPathD} L 400 120 L 0 120 Z`;

  // pH path: (0, 70) -> (100, 60) -> (200, 35) -> (300, 55) -> (400, 40)
  const phPathD = 'M 0 70 C 40 75, 70 60, 100 60 C 140 60, 170 30, 200 35 C 230 40, 260 60, 300 55 C 340 50, 370 42, 400 40';
  const phFillD = `${phPathD} L 400 120 L 0 120 Z`;

  const keyPointsSuhu = [
    { x: 0, y: 95 },
    { x: 100, y: 50 },
    { x: 200, y: 75 },
    { x: 300, y: 20 },
    { x: 400, y: 35 },
  ];

  const keyPointsPh = [
    { x: 0, y: 70 },
    { x: 100, y: 60 },
    { x: 200, y: 35 },
    { x: 300, y: 55 },
    { x: 400, y: 40 },
  ];

  return (
    <section className="bg-white rounded-2xl border border-[#bbcabf]/50 card-shadow overflow-hidden transition-all">
      {/* Header with Title and Mode Switcher */}
      <div className="p-4 border-b border-[#bbcabf]/30 bg-[#f8f9ff] flex flex-wrap justify-between items-center gap-2">
        <h3 className="text-base font-bold text-[#121c2a]">
          {metric === 'keduanya' ? 'Tren Suhu Air & pH' : 'Grafik Riwayat Sensor'}
        </h3>
        <div className="flex items-center gap-1.5">
          <button
            onClick={() => setMetric('suhu')}
            className={`px-3 py-1 rounded-full text-xs font-semibold transition-all active:scale-95 ${
              metric === 'suhu'
                ? 'bg-[#10b981] text-[#00422b] shadow-xs'
                : 'text-[#3c4a42] hover:bg-[#eff4ff]'
            }`}
          >
            Suhu Air
          </button>
          <button
            onClick={() => setMetric('ph')}
            className={`px-3 py-1 rounded-full text-xs font-semibold transition-all active:scale-95 ${
              metric === 'ph'
                ? 'bg-[#10b981] text-[#00422b] shadow-xs'
                : 'text-[#3c4a42] hover:bg-[#eff4ff]'
            }`}
          >
            pH
          </button>
          <button
            onClick={() => setMetric('keduanya')}
            className={`px-3 py-1 rounded-full text-xs font-semibold transition-all active:scale-95 ${
              metric === 'keduanya'
                ? 'bg-[#0058be] text-white shadow-xs'
                : 'text-[#3c4a42] hover:bg-[#eff4ff]'
            }`}
          >
            Keduanya
          </button>
        </div>
      </div>

      {/* SVG Chart Visualization */}
      <div className="p-5">
        <div className="relative h-44 w-full">
          {/* Subtle Horizontal Grid lines */}
          <div className="absolute inset-0 flex flex-col justify-between py-2 pointer-events-none opacity-40">
            <div className="w-full h-px border-b border-dashed border-[#bbcabf]" />
            <div className="w-full h-px border-b border-dashed border-[#bbcabf]" />
            <div className="w-full h-px border-b border-dashed border-[#bbcabf]" />
            <div className="w-full h-px border-b border-dashed border-[#bbcabf]" />
          </div>

          <svg
            className="w-full h-full overflow-visible"
            viewBox="0 0 400 120"
            preserveAspectRatio="none"
          >
            <defs>
              <linearGradient id="suhuGradient" x1="0%" y1="0%" x2="0%" y2="100%">
                <stop offset="0%" stopColor="#10b981" stopOpacity="0.35" />
                <stop offset="100%" stopColor="#10b981" stopOpacity="0.0" />
              </linearGradient>
              <linearGradient id="phGradient" x1="0%" y1="0%" x2="0%" y2="100%">
                <stop offset="0%" stopColor="#0058be" stopOpacity="0.30" />
                <stop offset="100%" stopColor="#0058be" stopOpacity="0.0" />
              </linearGradient>
            </defs>

            {/* Suhu Air Area & Path */}
            {(metric === 'suhu' || metric === 'keduanya') && (
              <>
                <path d={suhuFillD} fill="url(#suhuGradient)" />
                <path
                  d={suhuPathD}
                  fill="none"
                  stroke={metric === 'keduanya' ? '#0058be' : '#10b981'}
                  strokeWidth="3.2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
                {keyPointsSuhu.map((pt, idx) => (
                  <circle
                    key={`pt-suhu-${idx}`}
                    cx={pt.x}
                    cy={pt.y}
                    r={hoverIndex === idx ? 6 : 4}
                    fill="#ffffff"
                    stroke={metric === 'keduanya' ? '#0058be' : '#10b981'}
                    strokeWidth="2.5"
                    className="cursor-pointer transition-all"
                    onMouseEnter={() => setHoverIndex(idx)}
                    onMouseLeave={() => setHoverIndex(null)}
                  />
                ))}
              </>
            )}

            {/* pH Area & Path */}
            {(metric === 'ph' || metric === 'keduanya') && (
              <>
                <path d={phFillD} fill="url(#phGradient)" />
                <path
                  d={phPathD}
                  fill="none"
                  stroke="#10b981"
                  strokeWidth="3.2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
                {keyPointsPh.map((pt, idx) => (
                  <circle
                    key={`pt-ph-${idx}`}
                    cx={pt.x}
                    cy={pt.y}
                    r={hoverIndex === idx ? 6 : 4}
                    fill="#ffffff"
                    stroke="#10b981"
                    strokeWidth="2.5"
                    className="cursor-pointer transition-all"
                    onMouseEnter={() => setHoverIndex(idx)}
                    onMouseLeave={() => setHoverIndex(null)}
                  />
                ))}
              </>
            )}
          </svg>

          {/* Hover Value Tooltip Overlay */}
          {hoverIndex !== null && (
            <div
              className="absolute -top-3 transform -translate-x-1/2 bg-[#121c2a] text-white px-2.5 py-1 rounded-md text-xs font-semibold shadow-md pointer-events-none transition-all"
              style={{ left: `${(hoverIndex / 4) * 100}%` }}
            >
              {metric === 'keduanya' ? (
                <span>
                  Suhu: {suhuValues[hoverIndex]}°C · pH: {phValues[hoverIndex]}
                </span>
              ) : metric === 'suhu' ? (
                <span>{suhuValues[hoverIndex]} °C</span>
              ) : (
                <span>pH {phValues[hoverIndex]}</span>
              )}
            </div>
          )}

          {/* X-Axis Timestamps */}
          <div className="absolute -bottom-5 left-0 w-full flex justify-between px-0.5 text-[11px] font-medium text-[#6c7a71]">
            {timeLabels.map((t, idx) => (
              <span
                key={t}
                className={`cursor-pointer hover:text-[#006c49] transition-colors ${
                  hoverIndex === idx ? 'text-[#006c49] font-bold' : ''
                }`}
                onClick={() => setHoverIndex(hoverIndex === idx ? null : idx)}
              >
                {t}
              </span>
            ))}
          </div>
        </div>

        {/* Legend */}
        <div className="mt-8 flex items-center justify-center gap-6 text-xs text-[#3c4a42]">
          {(metric === 'suhu' || metric === 'keduanya') && (
            <div className="flex items-center gap-2">
              <span
                className={`w-3 h-3 rounded-full ${
                  metric === 'keduanya' ? 'bg-[#0058be]' : 'bg-[#10b981]'
                }`}
              />
              <span className="font-medium">
                {metric === 'keduanya' ? 'Suhu (°C)' : 'Suhu Air (°C)'}
              </span>
            </div>
          )}
          {(metric === 'ph' || metric === 'keduanya') && (
            <div className="flex items-center gap-2">
              <span className="w-3 h-3 rounded-full bg-[#10b981]" />
              <span className="font-medium">pH Larutan</span>
            </div>
          )}
        </div>
      </div>
    </section>
  );
};
