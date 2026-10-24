import React, { useState } from 'react';
import { SensorReading, PlantPhase, NutrientLog } from '../types';
import { SensorChart } from './SensorChart';
import { MoreVertical, RefreshCw, CheckCircle2, AlertTriangle } from 'lucide-react';

interface DashboardScreenProps {
  sensors: SensorReading;
  phase: PlantPhase;
  lastLog: NutrientLog | null;
  onOpenManualEntryModal: () => void;
  onNavigateToHistory: () => void;
  onRefreshSensors: () => void;
  isRefreshing?: boolean;
}

export const DashboardScreen: React.FC<DashboardScreenProps> = ({
  sensors,
  phase,
  lastLog,
  onOpenManualEntryModal,
  onNavigateToHistory,
  onRefreshSensors,
  isRefreshing = false,
}) => {
  const [menuOpen, setMenuOpen] = useState(false);
  const [iotStatus, setIotStatus] = useState<'online' | 'offline'>('online');

  const toggleIotStatus = () => {
    setIotStatus(prev => (prev === 'online' ? 'offline' : 'online'));
  };

  return (
    <div className="space-y-4 pb-12">
      {/* Title & Subtitle */}
      <div>
        <div className="flex items-center justify-between">
          <h2 className="text-2xl font-bold tracking-tight text-[#121c2a]">
            Dashboard Pengelola
          </h2>
          <button
            onClick={onRefreshSensors}
            disabled={isRefreshing}
            className={`p-1.5 rounded-lg text-[#3c4a42] hover:bg-[#eff4ff] transition-all active:scale-95 ${
              isRefreshing ? 'animate-spin text-[#006c49]' : ''
            }`}
            title="Sinkronisasi Data IoT"
          >
            <RefreshCw className="w-4 h-4" />
          </button>
        </div>
        <p className="text-sm text-[#3c4a42] mt-0.5">
          Ringkasan kondisi hidroponik saat ini.
        </p>
      </div>

      {/* Hero Section: Fase Tanaman & IoT Status */}
      <section className="relative bg-[#dee9fc] rounded-2xl p-5 border border-[#bbcabf]/50 overflow-hidden card-shadow">
        <div className="absolute inset-0 bg-pattern opacity-25 pointer-events-none" />
        <div className="relative z-10 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div>
            <h3 className="text-xs font-semibold uppercase tracking-wider text-[#3c4a42]">
              FASE TANAMAN
            </h3>
            <p className="text-3xl font-extrabold text-[#006c49] tracking-tight mt-0.5">
              {phase === 'vegetatif' ? 'VEGETATIF' : 'GENERATIF'}
            </p>
          </div>

          {/* IoT Status pill */}
          <button
            onClick={toggleIotStatus}
            className="flex items-center gap-2 bg-white rounded-full px-3.5 py-1.5 border border-[#bbcabf]/50 w-max shadow-2xs hover:bg-[#eff4ff] transition-colors"
            title="Klik untuk simulasi status koneksi IoT"
          >
            <span className="relative flex h-2.5 w-2.5">
              {iotStatus === 'online' && (
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-[#10b981] opacity-75" />
              )}
              <span
                className={`relative inline-flex rounded-full h-2.5 w-2.5 ${
                  iotStatus === 'online' ? 'bg-[#10b981]' : 'bg-[#ba1a1a]'
                }`}
              />
            </span>
            <span className="text-xs font-medium text-[#121c2a]">
              Status Perangkat IoT:{' '}
              <span className={iotStatus === 'online' ? 'font-semibold text-[#006c49]' : 'font-semibold text-[#ba1a1a]'}>
                {iotStatus === 'online' ? 'Aktif (Online)' : 'Terputus (Offline)'}
              </span>
            </span>
          </button>
        </div>
      </section>

      {/* Sensor Grid (2x2 Bento Style) */}
      <section className="grid grid-cols-2 gap-3">
        {/* Card 1: Suhu Air */}
        <div className="bg-white rounded-2xl p-4 border border-[#bbcabf]/50 card-shadow flex flex-col justify-between h-28 hover:border-[#006c49]/40 transition-colors">
          <div className="flex items-center gap-2">
            <div className="p-1.5 rounded-lg bg-[#eff4ff] text-[#0058be]">
              <span className="material-symbols-outlined fill text-[20px] text-[#0058be]">
                water_drop
              </span>
            </div>
            <span className="text-xs font-medium text-[#3c4a42]">Suhu Air</span>
          </div>
          <div className="flex items-baseline justify-end gap-1 text-right">
            <span className="text-2xl sm:text-3xl font-bold text-[#121c2a] tabular-nums">
              {sensors.waterTemp.toFixed(1)}
            </span>
            <span className="text-sm font-medium text-[#3c4a42]">°C</span>
          </div>
        </div>

        {/* Card 2: pH Larutan */}
        <div className="bg-white rounded-2xl p-4 border border-[#bbcabf]/50 card-shadow flex flex-col justify-between h-28 hover:border-[#006c49]/40 transition-colors">
          <div className="flex items-center gap-2">
            <div className="p-1.5 rounded-lg bg-[#eff4ff] text-[#006c49]">
              <span className="material-symbols-outlined fill text-[20px] text-[#006c49]">
                science
              </span>
            </div>
            <span className="text-xs font-medium text-[#3c4a42]">pH Larutan</span>
          </div>
          <div className="flex items-baseline justify-end gap-1 text-right">
            <span className="text-2xl sm:text-3xl font-bold text-[#121c2a] tabular-nums">
              {sensors.ph.toFixed(1)}
            </span>
          </div>
        </div>

        {/* Card 3: Suhu Ruang */}
        <div className="bg-white rounded-2xl p-4 border border-[#bbcabf]/50 card-shadow flex flex-col justify-between h-28 hover:border-[#006c49]/40 transition-colors">
          <div className="flex items-center gap-2">
            <div className="p-1.5 rounded-lg bg-[#eff4ff] text-[#a43a3a]">
              <span className="material-symbols-outlined fill text-[20px] text-[#a43a3a]">
                device_thermostat
              </span>
            </div>
            <span className="text-xs font-medium text-[#3c4a42]">Suhu Ruang</span>
          </div>
          <div className="flex items-baseline justify-end gap-1 text-right">
            <span className="text-2xl sm:text-3xl font-bold text-[#121c2a] tabular-nums">
              {sensors.roomTemp.toFixed(1)}
            </span>
            <span className="text-sm font-medium text-[#3c4a42]">°C</span>
          </div>
        </div>

        {/* Card 4: Kelembapan */}
        <div className="bg-white rounded-2xl p-4 border border-[#bbcabf]/50 card-shadow flex flex-col justify-between h-28 hover:border-[#006c49]/40 transition-colors">
          <div className="flex items-center gap-2">
            <div className="p-1.5 rounded-lg bg-[#eff4ff] text-[#2170e4]">
              <span className="material-symbols-outlined fill text-[20px] text-[#2170e4]">
                air
              </span>
            </div>
            <span className="text-xs font-medium text-[#3c4a42]">Kelembapan</span>
          </div>
          <div className="flex items-baseline justify-end gap-1 text-right">
            <span className="text-2xl sm:text-3xl font-bold text-[#121c2a] tabular-nums">
              {Math.round(sensors.humidity)}
            </span>
            <span className="text-sm font-medium text-[#3c4a42]">%</span>
          </div>
        </div>
      </section>

      {/* Sensor Trend Chart (Image 3 & Image 5) */}
      <SensorChart />

      {/* Latest Activity Card: Input Nutrisi Manual Terakhir */}
      <section className="bg-white rounded-2xl border border-[#bbcabf]/50 card-shadow overflow-hidden relative">
        <div className="p-4 border-b border-[#bbcabf]/30 bg-[#f8f9ff] flex justify-between items-center">
          <h3 className="text-base font-bold text-[#121c2a]">
            Input Nutrisi Manual Terakhir
          </h3>
          <div className="relative">
            <button
              onClick={() => setMenuOpen(!menuOpen)}
              className="p-1.5 rounded-full hover:bg-[#eff4ff] transition-colors text-[#3c4a42]"
              aria-label="Menu Opsi"
            >
              <MoreVertical className="w-4 h-4" />
            </button>

            {menuOpen && (
              <div className="absolute right-0 top-8 w-48 bg-white border border-[#bbcabf]/40 rounded-xl shadow-lg z-30 py-1 text-xs">
                <button
                  onClick={() => {
                    setMenuOpen(false);
                    onOpenManualEntryModal();
                  }}
                  className="w-full text-left px-3 py-2 text-[#121c2a] hover:bg-[#eff4ff] flex items-center gap-2"
                >
                  <span className="material-symbols-outlined text-sm text-[#006c49]">add_circle</span>
                  Input Nutrisi Baru
                </button>
                <button
                  onClick={() => {
                    setMenuOpen(false);
                    onNavigateToHistory();
                  }}
                  className="w-full text-left px-3 py-2 text-[#121c2a] hover:bg-[#eff4ff] flex items-center gap-2"
                >
                  <span className="material-symbols-outlined text-sm text-[#0058be]">history</span>
                  Lihat Riwayat Lengkap
                </button>
              </div>
            )}
          </div>
        </div>

        <div className="p-4 space-y-3">
          <div className="flex gap-3.5 items-start">
            <div className="p-2.5 bg-[#eff4ff] rounded-xl text-[#006c49] shrink-0">
              <span className="material-symbols-outlined fill text-[22px] text-[#006c49]">
                eco
              </span>
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center justify-between">
                <p className="text-sm font-semibold text-[#121c2a]">
                  {lastLog ? lastLog.dateStr : 'Hari ini'}
                </p>
                <span className="text-[11px] text-[#6c7a71]">
                  Fase: {lastLog ? lastLog.phase : phase}
                </span>
              </div>
              <div className="flex flex-col sm:flex-row sm:items-center gap-1 sm:gap-4 mt-1">
                <span className="text-xs text-[#3c4a42] flex items-center gap-1">
                  <span className="material-symbols-outlined text-[15px] text-[#6c7a71]">
                    schedule
                  </span>
                  Waktu Input: {lastLog ? lastLog.timeStr : '08:05 WIB'}
                </span>
                <span className="text-xs text-[#3c4a42] flex items-center gap-1">
                  <span className="material-symbols-outlined text-[15px] text-[#6c7a71]">
                    opacity
                  </span>
                  Nilai PPM:{' '}
                  <strong className="text-[#121c2a]">
                    {lastLog ? lastLog.ppm : 850}
                  </strong>
                </span>
              </div>
            </div>
          </div>

          {/* Status Badge */}
          <div className="pt-1 flex items-center justify-between">
            <div className="inline-flex items-center px-3 py-1 rounded-full bg-[#10b981]/15 text-[#00422b] border border-[#10b981]/30">
              <span className="w-1.5 h-1.5 rounded-full bg-[#006c49] mr-2 shrink-0 animate-pulse" />
              <span className="text-xs font-semibold text-[#006c49]">
                Status:{' '}
                {lastLog && lastLog.status === 'valid'
                  ? 'Tepat Waktu & Valid'
                  : lastLog
                  ? lastLog.statusLabel
                  : 'Tepat Waktu & Valid'}
              </span>
            </div>

            <button
              onClick={onOpenManualEntryModal}
              className="text-xs font-semibold text-[#006c49] hover:underline flex items-center gap-1"
            >
              + Catat Baru
            </button>
          </div>
        </div>
      </section>
    </div>
  );
};
