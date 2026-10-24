import React, { useState } from 'react';
import { ValidationSettings, PlantPhase } from '../types';
import { Save, Clock, Droplets, Radio, Check } from 'lucide-react';

interface SettingsScreenProps {
  settings: ValidationSettings;
  onSaveSettings: (newSettings: ValidationSettings) => void;
}

export const SettingsScreen: React.FC<SettingsScreenProps> = ({
  settings,
  onSaveSettings,
}) => {
  const [morningSchedule, setMorningSchedule] = useState(settings.morningSchedule);
  const [afternoonSchedule, setAfternoonSchedule] = useState(settings.afternoonSchedule);
  const [toleranceMinutes, setToleranceMinutes] = useState(settings.toleranceMinutes);

  const [activePhase, setActivePhase] = useState<PlantPhase>(settings.activePhase);

  const [ppmRanges, setPpmRanges] = useState(settings.ppmRanges);

  const [waterTempMin, setWaterTempMin] = useState(settings.sensorThresholds.waterTemp.min);
  const [waterTempMax, setWaterTempMax] = useState(settings.sensorThresholds.waterTemp.max);

  const [phMin, setPhMin] = useState(settings.sensorThresholds.ph.min);
  const [phMax, setPhMax] = useState(settings.sensorThresholds.ph.max);

  const [showSavedToast, setShowSavedToast] = useState(false);

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    const updated: ValidationSettings = {
      morningSchedule,
      afternoonSchedule,
      toleranceMinutes: Number(toleranceMinutes),
      activePhase,
      ppmRanges,
      sensorThresholds: {
        waterTemp: { min: Number(waterTempMin), max: Number(waterTempMax) },
        ph: { min: Number(phMin), max: Number(phMax) },
      },
    };

    onSaveSettings(updated);
    setShowSavedToast(true);
    setTimeout(() => {
      setShowSavedToast(false);
    }, 3000);
  };

  const handlePpmMinChange = (val: number) => {
    setPpmRanges(prev => ({
      ...prev,
      [activePhase]: { ...prev[activePhase], min: val },
    }));
  };

  const handlePpmMaxChange = (val: number) => {
    setPpmRanges(prev => ({
      ...prev,
      [activePhase]: { ...prev[activePhase], max: val },
    }));
  };

  return (
    <form onSubmit={handleSave} className="space-y-4 pb-16">
      {/* Title & Description */}
      <div>
        <h2 className="text-xl sm:text-2xl font-bold tracking-tight text-[#121c2a]">
          Pengaturan Parameter Validasi
        </h2>
        <p className="text-sm text-[#3c4a42] mt-0.5">
          Sesuaikan nilai ambang batas sistem.
        </p>
      </div>

      {/* Section 1: Jadwal Nutrisi */}
      <section className="bg-white border border-[#bbcabf]/50 rounded-2xl p-4 sm:p-5 card-shadow relative overflow-hidden">
        <div className="absolute top-0 right-0 w-32 h-32 bg-[#006c49]/5 rounded-bl-full pointer-events-none" />
        <div className="flex items-center gap-2 mb-4">
          <Clock className="w-5 h-5 text-[#006c49]" />
          <h3 className="text-base font-bold text-[#121c2a]">Jadwal Nutrisi</h3>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          {/* Jadwal Pagi */}
          <div className="space-y-1">
            <label className="text-[11px] font-semibold text-[#3c4a42] uppercase tracking-wider block">
              Jadwal Pagi
            </label>
            <div className="relative">
              <input
                type="time"
                value={morningSchedule}
                onChange={e => setMorningSchedule(e.target.value)}
                className="h-12 w-full px-3 pr-9 border border-[#bbcabf]/70 rounded-xl bg-[#f8f9ff] text-sm text-[#121c2a] font-medium focus:border-[#006c49] focus:ring-1 focus:ring-[#006c49] outline-none transition-all"
              />
              <Clock className="w-4 h-4 text-[#6c7a71] absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none" />
            </div>
          </div>

          {/* Jadwal Sore */}
          <div className="space-y-1">
            <label className="text-[11px] font-semibold text-[#3c4a42] uppercase tracking-wider block">
              Jadwal Sore
            </label>
            <div className="relative">
              <input
                type="time"
                value={afternoonSchedule}
                onChange={e => setAfternoonSchedule(e.target.value)}
                className="h-12 w-full px-3 pr-9 border border-[#bbcabf]/70 rounded-xl bg-[#f8f9ff] text-sm text-[#121c2a] font-medium focus:border-[#006c49] focus:ring-1 focus:ring-[#006c49] outline-none transition-all"
              />
              <Clock className="w-4 h-4 text-[#6c7a71] absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none" />
            </div>
          </div>

          {/* Toleransi */}
          <div className="space-y-1">
            <label className="text-[11px] font-semibold text-[#3c4a42] uppercase tracking-wider block">
              Toleransi (Menit)
            </label>
            <input
              type="number"
              min="1"
              max="120"
              value={toleranceMinutes}
              onChange={e => setToleranceMinutes(Number(e.target.value))}
              className="h-12 w-full px-3 border border-[#bbcabf]/70 rounded-xl bg-[#f8f9ff] text-sm text-[#121c2a] font-medium focus:border-[#006c49] focus:ring-1 focus:ring-[#006c49] outline-none transition-all"
            />
          </div>
        </div>
      </section>

      {/* Section 2: Rentang PPM Ideal */}
      <section className="bg-white border border-[#bbcabf]/50 rounded-2xl p-4 sm:p-5 card-shadow">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between mb-4 gap-3">
          <div className="flex items-center gap-2">
            <Droplets className="w-5 h-5 text-[#006c49]" />
            <h3 className="text-base font-bold text-[#121c2a]">Rentang PPM Ideal</h3>
          </div>

          {/* Segmented Control */}
          <div className="flex bg-[#d9e3f6] rounded-xl p-1 w-full sm:w-auto">
            <button
              type="button"
              onClick={() => setActivePhase('vegetatif')}
              className={`flex-1 sm:flex-none px-5 py-1.5 rounded-lg text-xs font-bold transition-all ${
                activePhase === 'vegetatif'
                  ? 'bg-white text-[#121c2a] shadow-xs'
                  : 'text-[#3c4a42] hover:text-[#121c2a]'
              }`}
            >
              Vegetatif
            </button>
            <button
              type="button"
              onClick={() => setActivePhase('generatif')}
              className={`flex-1 sm:flex-none px-5 py-1.5 rounded-lg text-xs font-bold transition-all ${
                activePhase === 'generatif'
                  ? 'bg-white text-[#121c2a] shadow-xs'
                  : 'text-[#3c4a42] hover:text-[#121c2a]'
              }`}
            >
              Generatif
            </button>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3">
          {/* Batas Bawah */}
          <div className="space-y-1">
            <label className="text-[11px] font-semibold text-[#3c4a42] uppercase tracking-wider block">
              Batas Bawah
            </label>
            <div className="relative">
              <input
                type="number"
                step="10"
                value={ppmRanges[activePhase].min}
                onChange={e => handlePpmMinChange(Number(e.target.value))}
                className="h-12 w-full px-3 pr-14 text-right border border-[#bbcabf]/70 rounded-xl bg-[#f8f9ff] text-sm text-[#121c2a] font-bold focus:border-[#006c49] focus:ring-1 focus:ring-[#006c49] outline-none"
              />
              <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs font-medium text-[#6c7a71]">
                PPM
              </span>
            </div>
          </div>

          {/* Batas Atas */}
          <div className="space-y-1">
            <label className="text-[11px] font-semibold text-[#3c4a42] uppercase tracking-wider block">
              Batas Atas
            </label>
            <div className="relative">
              <input
                type="number"
                step="10"
                value={ppmRanges[activePhase].max}
                onChange={e => handlePpmMaxChange(Number(e.target.value))}
                className="h-12 w-full px-3 pr-14 text-right border border-[#bbcabf]/70 rounded-xl bg-[#f8f9ff] text-sm text-[#121c2a] font-bold focus:border-[#006c49] focus:ring-1 focus:ring-[#006c49] outline-none"
              />
              <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs font-medium text-[#6c7a71]">
                PPM
              </span>
            </div>
          </div>
        </div>
      </section>

      {/* Section 3: Ambang Batas Sensor */}
      <section className="bg-white border border-[#bbcabf]/50 rounded-2xl p-4 sm:p-5 card-shadow space-y-6">
        <div className="flex items-center gap-2">
          <Radio className="w-5 h-5 text-[#006c49]" />
          <h3 className="text-base font-bold text-[#121c2a]">Ambang Batas Sensor</h3>
        </div>

        {/* Suhu Air */}
        <div className="space-y-3">
          <div className="flex justify-between items-end">
            <label className="text-sm font-semibold text-[#121c2a]">Suhu Air</label>
            <span className="text-base font-bold text-[#006c49] tabular-nums">
              {waterTempMin} - {waterTempMax} °C
            </span>
          </div>

          {/* Simulated Range Slider Track */}
          <div className="relative h-6 flex items-center">
            {/* Background Track */}
            <div className="absolute w-full h-1.5 bg-[#d9e3f6] rounded-full" />
            {/* Active Range Track */}
            <div
              className="absolute h-1.5 bg-[#006c49] rounded-full"
              style={{
                left: `${(waterTempMin / 50) * 100}%`,
                width: `${((waterTempMax - waterTempMin) / 50) * 100}%`,
              }}
            />
            {/* Left Thumb */}
            <div
              className="absolute w-5 h-5 bg-white border-2 border-[#006c49] rounded-full shadow-md -ml-2.5 cursor-pointer"
              style={{ left: `${(waterTempMin / 50) * 100}%` }}
            />
            {/* Right Thumb */}
            <div
              className="absolute w-5 h-5 bg-white border-2 border-[#006c49] rounded-full shadow-md -ml-2.5 cursor-pointer"
              style={{ left: `${(waterTempMax / 50) * 100}%` }}
            />
          </div>

          <div className="flex justify-between text-[11px] font-medium text-[#6c7a71]">
            <span>0°C</span>
            <div className="flex items-center gap-2">
              <span className="text-xs text-[#3c4a42]">Atur:</span>
              <input
                type="number"
                min="0"
                max={waterTempMax - 1}
                value={waterTempMin}
                onChange={e => setWaterTempMin(Number(e.target.value))}
                className="w-14 h-7 text-center rounded-lg border border-[#bbcabf] bg-[#f8f9ff] text-xs font-semibold"
              />
              <span>-</span>
              <input
                type="number"
                min={waterTempMin + 1}
                max="50"
                value={waterTempMax}
                onChange={e => setWaterTempMax(Number(e.target.value))}
                className="w-14 h-7 text-center rounded-lg border border-[#bbcabf] bg-[#f8f9ff] text-xs font-semibold"
              />
            </div>
            <span>50°C</span>
          </div>
        </div>

        <div className="border-t border-[#bbcabf]/30" />

        {/* pH Larutan */}
        <div className="space-y-3">
          <div className="flex justify-between items-end">
            <label className="text-sm font-semibold text-[#121c2a]">pH Larutan</label>
            <span className="text-base font-bold text-[#006c49] tabular-nums">
              {phMin.toFixed(1)} - {phMax.toFixed(1)}
            </span>
          </div>

          {/* Simulated Range Slider Track */}
          <div className="relative h-6 flex items-center">
            {/* Background Track */}
            <div className="absolute w-full h-1.5 bg-[#d9e3f6] rounded-full" />
            {/* Active Range Track */}
            <div
              className="absolute h-1.5 bg-[#006c49] rounded-full"
              style={{
                left: `${(phMin / 14) * 100}%`,
                width: `${((phMax - phMin) / 14) * 100}%`,
              }}
            />
            {/* Left Thumb */}
            <div
              className="absolute w-5 h-5 bg-white border-2 border-[#006c49] rounded-full shadow-md -ml-2.5 cursor-pointer"
              style={{ left: `${(phMin / 14) * 100}%` }}
            />
            {/* Right Thumb */}
            <div
              className="absolute w-5 h-5 bg-white border-2 border-[#006c49] rounded-full shadow-md -ml-2.5 cursor-pointer"
              style={{ left: `${(phMax / 14) * 100}%` }}
            />
          </div>

          <div className="flex justify-between text-[11px] font-medium text-[#6c7a71]">
            <span>0</span>
            <div className="flex items-center gap-2">
              <span className="text-xs text-[#3c4a42]">Atur:</span>
              <input
                type="number"
                step="0.1"
                min="0"
                max={phMax - 0.2}
                value={phMin}
                onChange={e => setPhMin(Number(e.target.value))}
                className="w-14 h-7 text-center rounded-lg border border-[#bbcabf] bg-[#f8f9ff] text-xs font-semibold"
              />
              <span>-</span>
              <input
                type="number"
                step="0.1"
                min={phMin + 0.2}
                max="14"
                value={phMax}
                onChange={e => setPhMax(Number(e.target.value))}
                className="w-14 h-7 text-center rounded-lg border border-[#bbcabf] bg-[#f8f9ff] text-xs font-semibold"
              />
            </div>
            <span>14</span>
          </div>
        </div>
      </section>

      {/* Save Button */}
      <div className="pt-2">
        <button
          type="submit"
          className="w-full h-14 bg-[#10b981] hover:bg-[#006c49] text-white font-bold text-base rounded-2xl shadow-md transition-all active:scale-[0.98] flex items-center justify-center gap-2 cursor-pointer"
        >
          <Save className="w-5 h-5" />
          <span>Simpan Pengaturan</span>
        </button>
      </div>

      {/* Toast confirmation */}
      {showSavedToast && (
        <div className="fixed bottom-24 left-1/2 -translate-x-1/2 z-50 bg-[#00422b] text-white px-5 py-3 rounded-2xl shadow-xl flex items-center gap-2 text-sm font-semibold animate-bounce border border-[#10b981]">
          <Check className="w-4 h-4 text-[#10b981]" />
          <span>Pengaturan parameter berhasil disimpan!</span>
        </div>
      )}
    </form>
  );
};
