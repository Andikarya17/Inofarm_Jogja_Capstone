import React, { useState, useEffect } from 'react';
import { TabKey, SensorReading, NutrientLog, ValidationSettings } from './types';
import { DEFAULT_SETTINGS, INITIAL_LOGS } from './utils/validation';
import { TopAppBar } from './components/TopAppBar';
import { BottomNavBar } from './components/BottomNavBar';
import { DashboardScreen } from './components/DashboardScreen';
import { HistoryScreen } from './components/HistoryScreen';
import { SettingsScreen } from './components/SettingsScreen';
import { ManualEntryModal } from './components/ManualEntryModal';
import { KotlinCodeViewer } from './components/KotlinCodeViewer';
import { NotificationsModal } from './components/NotificationsModal';
import { Smartphone, Code2, Wifi, Battery, Sparkles } from 'lucide-react';

export default function App() {
  const [currentTab, setCurrentTab] = useState<TabKey>('beranda');

  // Persistence for Settings
  const [settings, setSettings] = useState<ValidationSettings>(() => {
    try {
      const saved = localStorage.getItem('inofarm_settings');
      return saved ? JSON.parse(saved) : DEFAULT_SETTINGS;
    } catch {
      return DEFAULT_SETTINGS;
    }
  });

  // Persistence for Logs
  const [logs, setLogs] = useState<NutrientLog[]>(() => {
    try {
      const saved = localStorage.getItem('inofarm_logs');
      return saved ? JSON.parse(saved) : INITIAL_LOGS;
    } catch {
      return INITIAL_LOGS;
    }
  });

  // Sensor Readings State
  const [sensors, setSensors] = useState<SensorReading>({
    waterTemp: 24.5,
    ph: 6.2,
    roomTemp: 28.0,
    humidity: 72,
    timestamp: '08:05 WIB',
  });

  const [isRefreshingSensors, setIsRefreshingSensors] = useState(false);
  const [unreadNotifications, setUnreadNotifications] = useState(2);

  // Modals
  const [isManualEntryOpen, setIsManualEntryOpen] = useState(false);
  const [isKotlinModalOpen, setIsKotlinModalOpen] = useState(false);
  const [isNotificationsOpen, setIsNotificationsOpen] = useState(false);

  // Mobile Device Frame Mode
  const [isMobileDeviceView, setIsMobileDeviceView] = useState(true);

  // Persist settings
  useEffect(() => {
    try {
      localStorage.setItem('inofarm_settings', JSON.stringify(settings));
    } catch (e) {
      console.error(e);
    }
  }, [settings]);

  // Persist logs
  useEffect(() => {
    try {
      localStorage.setItem('inofarm_logs', JSON.stringify(logs));
    } catch (e) {
      console.error(e);
    }
  }, [logs]);

  // Refresh / Simulate Sensor telemetry fluctuation
  const handleRefreshSensors = () => {
    setIsRefreshingSensors(true);
    setTimeout(() => {
      setSensors(prev => ({
        ...prev,
        waterTemp: Number((24.2 + Math.random() * 0.6).toFixed(1)),
        ph: Number((6.1 + Math.random() * 0.3).toFixed(1)),
        roomTemp: Number((27.8 + Math.random() * 0.5).toFixed(1)),
        humidity: Math.round(70 + Math.random() * 4),
      }));
      setIsRefreshingSensors(false);
    }, 600);
  };

  // Add new nutrient log
  const handleAddLog = (newLog: NutrientLog) => {
    setLogs(prev => [newLog, ...prev]);
  };

  // Export CSV
  const handleExportCsv = () => {
    const headers = ['ID,Tanggal,Waktu,PPM,Fase,Status,Petugas,Catatan\n'];
    const rows = logs.map(l =>
      `"${l.id}","${l.dateStr}","${l.timeStr}",${l.ppm},"${l.phase}","${l.statusLabel}","${l.recordedBy || '-'}","${l.notes || '-'}"\n`
    );
    const csvContent = headers.concat(rows).join('');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute('download', `riwayat-nutrisi-inofarm-${Date.now()}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const lastLog = logs.length > 0 ? logs[0] : null;

  return (
    <div className="min-h-screen bg-[#121c2a] text-[#121c2a] flex flex-col items-center justify-start p-0 md:py-6 selection:bg-[#10b981] selection:text-white">
      {/* Top Banner on Desktop highlighting Kotlin Code Available */}
      <div className="w-full max-w-4xl px-4 py-2 hidden md:flex items-center justify-between text-xs text-[#d9e3f6]">
        <div className="flex items-center gap-2">
          <span className="w-2 h-2 rounded-full bg-[#10b981] animate-ping" />
          <span className="font-semibold text-white">Inofarm Jogja Mobile UI (MelonNutri)</span>
          <span className="text-[#bbcabf]">· Target Techstack: Kotlin &amp; Jetpack Compose</span>
        </div>
        <div className="flex items-center gap-3">
          <button
            onClick={() => setIsKotlinModalOpen(true)}
            className="flex items-center gap-1.5 px-3 py-1 bg-[#10b981] text-[#00422b] font-bold rounded-full hover:bg-[#4edea3] transition-colors shadow-xs"
          >
            <Code2 className="w-3.5 h-3.5" />
            <span>Lihat Kode Kotlin (.kt)</span>
          </button>
          <button
            onClick={() => setIsMobileDeviceView(!isMobileDeviceView)}
            className="text-[#d9e3f6] hover:text-white underline cursor-pointer"
          >
            {isMobileDeviceView ? 'Mode Lebar (Fluid)' : 'Mode Device Android'}
          </button>
        </div>
      </div>

      {/* Main Container / Mobile Frame Wrapper */}
      <div
        className={`w-full transition-all duration-300 relative ${
          isMobileDeviceView
            ? 'max-w-[430px] min-h-[880px] bg-[#f8f9ff] md:rounded-[44px] md:shadow-2xl md:border-[10px] md:border-[#27313f] md:overflow-hidden flex flex-col'
            : 'max-w-4xl min-h-screen bg-[#f8f9ff] md:rounded-3xl md:shadow-xl md:border border-[#bbcabf]/30 flex flex-col'
        }`}
      >
        {/* Android Device Status Bar (Simulated when in device frame) */}
        {isMobileDeviceView && (
          <div className="h-7 w-full bg-[#f8f9ff] px-6 pt-1 flex items-center justify-between text-[11px] font-semibold text-[#121c2a] select-none z-50">
            <span>08:05</span>
            {/* Center Camera Punch hole */}
            <div className="w-3 h-3 rounded-full bg-[#121c2a]/90" />
            <div className="flex items-center gap-1.5 text-xs text-[#121c2a]">
              <Wifi className="w-3.5 h-3.5" />
              <span className="text-[10px]">5G</span>
              <Battery className="w-4 h-4" />
            </div>
          </div>
        )}

        {/* Top App Bar */}
        <TopAppBar
          unreadCount={unreadNotifications}
          onOpenNotifications={() => setIsNotificationsOpen(true)}
          onOpenKotlinModal={() => setIsKotlinModalOpen(true)}
          isMobileDeviceView={isMobileDeviceView}
          onToggleDeviceView={() => setIsMobileDeviceView(!isMobileDeviceView)}
        />

        {/* Screen View Body */}
        <main className="flex-1 px-4 pt-4 pb-24 overflow-y-auto">
          {currentTab === 'beranda' && (
            <DashboardScreen
              sensors={sensors}
              phase={settings.activePhase}
              lastLog={lastLog}
              onOpenManualEntryModal={() => setIsManualEntryOpen(true)}
              onNavigateToHistory={() => setCurrentTab('riwayat')}
              onRefreshSensors={handleRefreshSensors}
              isRefreshing={isRefreshingSensors}
            />
          )}

          {currentTab === 'riwayat' && (
            <HistoryScreen
              logs={logs}
              onOpenManualEntryModal={() => setIsManualEntryOpen(true)}
              onExportCsv={handleExportCsv}
            />
          )}

          {currentTab === 'pengaturan' && (
            <SettingsScreen
              settings={settings}
              onSaveSettings={newSettings => setSettings(newSettings)}
            />
          )}
        </main>

        {/* Bottom Nav Bar */}
        <BottomNavBar
          currentTab={currentTab}
          onSelectTab={tab => setCurrentTab(tab)}
        />
      </div>

      {/* Floating Kotlin Quick Trigger on Mobile Bottom Right */}
      <button
        onClick={() => setIsKotlinModalOpen(true)}
        className="fixed bottom-20 right-4 z-40 bg-[#006c49] text-white p-3 rounded-full shadow-xl hover:bg-[#005236] transition-all active:scale-95 border-2 border-[#10b981] flex items-center gap-2 md:hidden"
        title="Lihat Kode Kotlin Android"
      >
        <Code2 className="w-5 h-5 text-[#4edea3]" />
      </button>

      {/* Modals */}
      <ManualEntryModal
        isOpen={isManualEntryOpen}
        onClose={() => setIsManualEntryOpen(false)}
        onAddLog={handleAddLog}
        currentSettings={settings}
        activePhase={settings.activePhase}
      />

      <KotlinCodeViewer
        isOpen={isKotlinModalOpen}
        onClose={() => setIsKotlinModalOpen(false)}
      />

      <NotificationsModal
        isOpen={isNotificationsOpen}
        onClose={() => setIsNotificationsOpen(false)}
        onClearAll={() => setUnreadNotifications(0)}
      />
    </div>
  );
}
