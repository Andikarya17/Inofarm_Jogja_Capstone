import React, { useState, useId } from 'react';
import { PlantPhase, ValidationSettings, NutrientLog } from '../types';
import { validateNutrientInput } from '../utils/validation';
import { X, CheckCircle, AlertTriangle, AlertCircle, Clock, Droplets } from 'lucide-react';

interface ManualEntryModalProps {
  isOpen: boolean;
  onClose: () => void;
  onAddLog: (newLog: NutrientLog) => void;
  currentSettings: ValidationSettings;
  activePhase: PlantPhase;
}

export const ManualEntryModal: React.FC<ManualEntryModalProps> = ({
  isOpen,
  onClose,
  onAddLog,
  currentSettings,
  activePhase,
}) => {
  const [time, setTime] = useState('08:05');
  const [ppm, setPpm] = useState<number>(850);
  const [phase, setPhase] = useState<PlantPhase>(activePhase);
  const [notes, setNotes] = useState('');
  const [recordedBy, setRecordedBy] = useState('Operator Inofarm');

  if (!isOpen) return null;

  // Real-time validation feedback
  const validationResult = validateNutrientInput(time, ppm, phase, currentSettings);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    const now = new Date();
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'Mei', 'Jun', 'Jul', 'Agt', 'Sep', 'Okt', 'Nov', 'Des'];
    const dateStr = `${now.getDate()} ${months[now.getMonth()]} ${now.getFullYear()}`;
    const timeStr = `${time} WIB`;

    const newLog: NutrientLog = {
      id: `log-${Date.now()}`,
      dateStr,
      timeStr,
      timestamp: Date.now(),
      ppm: Number(ppm),
      phase,
      status: validationResult.status,
      statusLabel: validationResult.statusLabel,
      notes: notes || validationResult.reason,
      recordedBy,
    };

    onAddLog(newLog);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-xs flex items-center justify-center p-4">
      <div className="bg-white w-full max-w-md rounded-3xl p-6 shadow-2xl border border-[#bbcabf]/50 space-y-4 max-h-[90vh] overflow-y-auto">
        {/* Modal Header */}
        <div className="flex items-center justify-between border-b border-[#bbcabf]/30 pb-3">
          <div className="flex items-center gap-2">
            <div className="p-2 bg-[#eff4ff] text-[#006c49] rounded-xl">
              <span className="material-symbols-outlined text-[20px] text-[#006c49]">eco</span>
            </div>
            <div>
              <h3 className="text-base font-bold text-[#121c2a]">
                Input Nutrisi Manual
              </h3>
              <p className="text-xs text-[#3c4a42]">
                Validasi otomatis berdasarkan parameter aktif
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-full hover:bg-[#eff4ff] text-[#6c7a71] transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Fase Tanaman */}
          <div className="space-y-1">
            <label className="text-xs font-semibold text-[#3c4a42] uppercase tracking-wider block">
              Fase Tanaman
            </label>
            <div className="flex bg-[#d9e3f6] rounded-xl p-1">
              <button
                type="button"
                onClick={() => setPhase('vegetatif')}
                className={`flex-1 py-1.5 rounded-lg text-xs font-bold transition-all ${
                  phase === 'vegetatif'
                    ? 'bg-white text-[#121c2a] shadow-xs'
                    : 'text-[#3c4a42]'
                }`}
              >
                Vegetatif (800 - 1000 PPM)
              </button>
              <button
                type="button"
                onClick={() => setPhase('generatif')}
                className={`flex-1 py-1.5 rounded-lg text-xs font-bold transition-all ${
                  phase === 'generatif'
                    ? 'bg-white text-[#121c2a] shadow-xs'
                    : 'text-[#3c4a42]'
                }`}
              >
                Generatif (1100 - 1400 PPM)
              </button>
            </div>
          </div>

          {/* Waktu Input */}
          <div className="space-y-1">
            <label className="text-xs font-semibold text-[#3c4a42] uppercase tracking-wider block">
              Waktu Pengukuran (WIB)
            </label>
            <div className="relative">
              <input
                type="time"
                value={time}
                onChange={e => setTime(e.target.value)}
                required
                className="h-12 w-full px-3 pr-10 rounded-xl border border-[#bbcabf] bg-[#f8f9ff] text-sm font-semibold focus:border-[#006c49] outline-none"
              />
              <Clock className="w-4 h-4 text-[#6c7a71] absolute right-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
            </div>
            <p className="text-[11px] text-[#6c7a71]">
              Jadwal ideal: {currentSettings.morningSchedule} & {currentSettings.afternoonSchedule} (±{currentSettings.toleranceMinutes} mnt)
            </p>
          </div>

          {/* Nilai PPM */}
          <div className="space-y-1">
            <label className="text-xs font-semibold text-[#3c4a42] uppercase tracking-wider block">
              Nilai PPM Terukur
            </label>
            <div className="relative">
              <input
                type="number"
                min="100"
                max="3000"
                step="10"
                value={ppm}
                onChange={e => setPpm(Number(e.target.value))}
                required
                className="h-12 w-full px-3 pr-16 text-right rounded-xl border border-[#bbcabf] bg-[#f8f9ff] text-base font-bold text-[#121c2a] focus:border-[#006c49] outline-none"
              />
              <span className="absolute right-3.5 top-1/2 -translate-y-1/2 text-xs font-bold text-[#6c7a71]">
                PPM
              </span>
            </div>
          </div>

          {/* Live Validation Alert Preview */}
          <div
            className={`p-3.5 rounded-2xl border text-xs space-y-1 ${
              validationResult.status === 'valid'
                ? 'bg-[#10b981]/15 border-[#10b981]/40 text-[#00422b]'
                : validationResult.status === 'late'
                ? 'bg-[#ffdad6] border-[#ba1a1a]/30 text-[#93000a]'
                : validationResult.status === 'low'
                ? 'bg-[#fef08a] border-[#ca8a04]/40 text-[#854d0e]'
                : 'bg-[#fed7aa] border-[#ea580c]/40 text-[#9a3412]'
            }`}
          >
            <div className="flex items-center gap-1.5 font-bold text-sm">
              {validationResult.status === 'valid' ? (
                <CheckCircle className="w-4 h-4 text-[#006c49]" />
              ) : (
                <AlertTriangle className="w-4 h-4" />
              )}
              <span>Hasil Validasi: {validationResult.statusLabel}</span>
            </div>
            <p className="opacity-90">{validationResult.reason}</p>
          </div>

          {/* Catatan Tambahan (Opsional) */}
          <div className="space-y-1">
            <label className="text-xs font-semibold text-[#3c4a42] uppercase tracking-wider block">
              Catatan / Kondisi (Opsional)
            </label>
            <input
              type="text"
              placeholder="Contoh: Larutan stabil, aerator berjalan optimal..."
              value={notes}
              onChange={e => setNotes(e.target.value)}
              className="h-11 w-full px-3 rounded-xl border border-[#bbcabf] bg-[#f8f9ff] text-xs focus:border-[#006c49] outline-none"
            />
          </div>

          {/* Petugas Penginput */}
          <div className="space-y-1">
            <label className="text-xs font-semibold text-[#3c4a42] uppercase tracking-wider block">
              Petugas / Operator
            </label>
            <input
              type="text"
              value={recordedBy}
              onChange={e => setRecordedBy(e.target.value)}
              className="h-11 w-full px-3 rounded-xl border border-[#bbcabf] bg-[#f8f9ff] text-xs focus:border-[#006c49] outline-none"
            />
          </div>

          {/* Submit Button */}
          <div className="pt-2 flex gap-3">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 py-3 rounded-xl border border-[#bbcabf] text-sm font-semibold text-[#3c4a42] hover:bg-[#eff4ff]"
            >
              Batal
            </button>
            <button
              type="submit"
              className="flex-1 py-3 rounded-xl bg-[#006c49] text-white text-sm font-bold hover:bg-[#005236] shadow-md transition-all active:scale-95 cursor-pointer"
            >
              Simpan Catatan
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
