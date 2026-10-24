import React, { useState, useMemo } from 'react';
import { NutrientLog, NutrientLogStatus } from '../types';
import { Calendar, Download, Plus, CheckCircle2, AlertCircle, AlertTriangle, AlertOctagon, Filter, X } from 'lucide-react';

interface HistoryScreenProps {
  logs: NutrientLog[];
  onOpenManualEntryModal: () => void;
  onExportCsv: () => void;
}

export const HistoryScreen: React.FC<HistoryScreenProps> = ({
  logs,
  onOpenManualEntryModal,
  onExportCsv,
}) => {
  const [activeFilter, setActiveFilter] = useState<'all' | 'valid' | 'abnormal'>('all');
  const [filterModalOpen, setFilterModalOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [specificStatusFilter, setSpecificStatusFilter] = useState<NutrientLogStatus | 'all'>('all');

  // Counters
  const totalCount = logs.length;
  const validCount = logs.filter(l => l.status === 'valid').length;
  const abnormalCount = logs.filter(l => l.status !== 'valid').length;

  // Filtered list
  const filteredLogs = useMemo(() => {
    return logs.filter(item => {
      // 1. Tab filter
      if (activeFilter === 'valid' && item.status !== 'valid') return false;
      if (activeFilter === 'abnormal' && item.status === 'valid') return false;

      // 2. Specific status filter if chosen in modal
      if (specificStatusFilter !== 'all' && item.status !== specificStatusFilter) return false;

      // 3. Search query
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        const matchesDate = item.dateStr.toLowerCase().includes(q);
        const matchesPpm = item.ppm.toString().includes(q);
        const matchesStatus = item.statusLabel.toLowerCase().includes(q);
        return matchesDate || matchesPpm || matchesStatus;
      }

      return true;
    });
  }, [logs, activeFilter, specificStatusFilter, searchQuery]);

  return (
    <div className="space-y-4 pb-12">
      {/* Title & Description */}
      <div>
        <h2 className="text-2xl font-bold tracking-tight text-[#121c2a]">
          Riwayat Pemberian Nutrisi
        </h2>
        <p className="text-sm text-[#3c4a42] mt-0.5">
          Lacak dan validasi pencatatan nutrisi harian.
        </p>
      </div>

      {/* Action Buttons: Filter & Ekspor + Add New */}
      <div className="grid grid-cols-2 gap-3">
        <button
          onClick={() => setFilterModalOpen(true)}
          className={`flex items-center justify-center gap-2 h-12 rounded-xl border-2 border-[#006c49] font-semibold text-sm transition-all active:scale-95 ${
            specificStatusFilter !== 'all' || searchQuery
              ? 'bg-[#006c49] text-white shadow-xs'
              : 'text-[#006c49] hover:bg-[#eff4ff]'
          }`}
        >
          <Calendar className="w-4 h-4" />
          <span>Filter {specificStatusFilter !== 'all' || searchQuery ? '(Aktif)' : ''}</span>
        </button>

        <button
          onClick={onExportCsv}
          className="flex items-center justify-center gap-2 h-12 rounded-xl border-2 border-[#006c49] text-[#006c49] font-semibold text-sm hover:bg-[#eff4ff] transition-all active:scale-95"
        >
          <Download className="w-4 h-4" />
          <span>Ekspor</span>
        </button>
      </div>

      {/* Summary Chips Row matching Screenshot 7 */}
      <div className="flex flex-wrap items-center gap-2">
        {/* Total Chip */}
        <button
          onClick={() => setActiveFilter('all')}
          className={`px-4 py-1.5 rounded-full text-xs font-semibold border transition-all active:scale-95 ${
            activeFilter === 'all'
              ? 'bg-[#121c2a] text-white border-[#121c2a] shadow-xs'
              : 'bg-[#eff4ff] text-[#121c2a] border-[#bbcabf]/50 hover:bg-[#d9e3f6]'
          }`}
        >
          Total: <span className="font-bold ml-1">{totalCount}</span>
        </button>

        {/* Valid Chip */}
        <button
          onClick={() => setActiveFilter(activeFilter === 'valid' ? 'all' : 'valid')}
          className={`px-4 py-1.5 rounded-full text-xs font-semibold border transition-all active:scale-95 ${
            activeFilter === 'valid'
              ? 'bg-[#006c49] text-white border-[#006c49] shadow-xs'
              : 'bg-[#10b981]/15 text-[#006c49] border-[#10b981]/40 hover:bg-[#10b981]/25'
          }`}
        >
          Valid: <span className="font-bold ml-1">{validCount}</span>
        </button>

        {/* Abnormal Chip */}
        <button
          onClick={() => setActiveFilter(activeFilter === 'abnormal' ? 'all' : 'abnormal')}
          className={`px-4 py-1.5 rounded-full text-xs font-semibold border transition-all active:scale-95 ${
            activeFilter === 'abnormal'
              ? 'bg-[#ba1a1a] text-white border-[#ba1a1a] shadow-xs'
              : 'bg-[#ffdad6] text-[#ba1a1a] border-[#ba1a1a]/30 hover:bg-[#ffdad6]/80'
          }`}
        >
          Abnormal: <span className="font-bold ml-1">{abnormalCount}</span>
        </button>

        {/* Quick Add Log Button */}
        <button
          onClick={onOpenManualEntryModal}
          className="ml-auto flex items-center gap-1 px-3 py-1.5 rounded-full bg-[#10b981] text-[#00422b] text-xs font-bold hover:bg-[#006c49] hover:text-white transition-colors active:scale-95 shadow-2xs"
        >
          <Plus className="w-3.5 h-3.5" />
          <span>+ Catat PPM</span>
        </button>
      </div>

      {/* Filter Info banner if applied */}
      {(searchQuery || specificStatusFilter !== 'all') && (
        <div className="flex items-center justify-between px-3 py-2 bg-[#dee9fc] rounded-xl text-xs text-[#121c2a]">
          <span>
            Filter: {searchQuery && `"${searchQuery}"`}
            {searchQuery && specificStatusFilter !== 'all' && ' & '}
            {specificStatusFilter !== 'all' && `Status: ${specificStatusFilter}`}
          </span>
          <button
            onClick={() => {
              setSearchQuery('');
              setSpecificStatusFilter('all');
            }}
            className="text-[#ba1a1a] font-bold hover:underline"
          >
            Reset
          </button>
        </div>
      )}

      {/* Log Cards List */}
      <div className="space-y-3">
        {filteredLogs.length === 0 ? (
          <div className="text-center py-12 bg-white rounded-2xl border border-[#bbcabf]/40 p-6">
            <span className="material-symbols-outlined text-4xl text-[#6c7a71]">
              content_paste_off
            </span>
            <p className="text-sm font-semibold text-[#121c2a] mt-2">
              Tidak ada data riwayat yang cocok
            </p>
            <p className="text-xs text-[#6c7a71] mt-1">
              Coba atur ulang filter atau tambahkan pencatatan PPM baru.
            </p>
            <button
              onClick={() => {
                setActiveFilter('all');
                setSearchQuery('');
                setSpecificStatusFilter('all');
              }}
              className="mt-4 px-4 py-2 bg-[#006c49] text-white rounded-xl text-xs font-semibold"
            >
              Tampilkan Semua Riwayat
            </button>
          </div>
        ) : (
          filteredLogs.map(item => (
            <div
              key={item.id}
              className="bg-white border border-[#bbcabf]/50 rounded-2xl p-4 card-shadow hover:border-[#006c49]/40 transition-all"
            >
              {/* Header Timestamp */}
              <div className="flex items-center gap-1.5 text-xs text-[#6c7a71] mb-1">
                <span className="material-symbols-outlined text-[15px]">
                  schedule
                </span>
                <span>
                  {item.dateStr}, {item.timeStr}
                </span>
              </div>

              {/* PPM Value & Phase */}
              <div className="text-base font-bold text-[#121c2a]">
                PPM Manual: {item.ppm}
              </div>
              <div className="text-xs text-[#3c4a42] capitalize mt-0.5">
                Fase: {item.phase}
              </div>

              {/* Notes / Reason if abnormal */}
              {item.notes && item.status !== 'valid' && (
                <p className="text-[11px] text-[#6c7a71] mt-1 italic">
                  "{item.notes}"
                </p>
              )}

              {/* Status Badge Row */}
              <div className="mt-3 flex items-center justify-between">
                {/* Specific Semantic Badge per Image 7 */}
                {item.status === 'valid' && (
                  <span className="inline-flex items-center gap-1.5 bg-[#10b981]/15 text-[#006c49] text-xs font-semibold px-3 py-1 rounded-full border border-[#10b981]/30">
                    <CheckCircle2 className="w-3.5 h-3.5 text-[#006c49]" />
                    <span>Sudah Tervalidasi</span>
                  </span>
                )}

                {item.status === 'late' && (
                  <span className="inline-flex items-center gap-1.5 bg-[#ffdad6] text-[#ba1a1a] text-xs font-semibold px-3 py-1 rounded-full border border-[#ba1a1a]/30">
                    <AlertCircle className="w-3.5 h-3.5 text-[#ba1a1a]" />
                    <span>Terlambat Dicatat</span>
                  </span>
                )}

                {item.status === 'low' && (
                  <span className="inline-flex items-center gap-1.5 bg-[#fef08a] text-[#854d0e] text-xs font-semibold px-3 py-1 rounded-full border border-[#ca8a04]/40">
                    <AlertTriangle className="w-3.5 h-3.5 text-[#854d0e]" />
                    <span>PPM Rendah</span>
                  </span>
                )}

                {item.status === 'high' && (
                  <span className="inline-flex items-center gap-1.5 bg-[#fed7aa] text-[#9a3412] text-xs font-semibold px-3 py-1 rounded-full border border-[#ea580c]/40">
                    <AlertOctagon className="w-3.5 h-3.5 text-[#9a3412]" />
                    <span>PPM Tinggi</span>
                  </span>
                )}

                {item.recordedBy && (
                  <span className="text-[11px] text-[#6c7a71]">
                    Oleh: {item.recordedBy.split(' ')[0]}
                  </span>
                )}
              </div>
            </div>
          ))
        )}
      </div>

      {/* Filter Modal */}
      {filterModalOpen && (
        <div className="fixed inset-0 z-50 bg-black/40 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white w-full max-w-sm rounded-2xl p-5 shadow-2xl border border-[#bbcabf]/40 space-y-4">
            <div className="flex items-center justify-between border-b border-[#bbcabf]/30 pb-3">
              <h3 className="text-base font-bold text-[#121c2a] flex items-center gap-2">
                <Filter className="w-4 h-4 text-[#006c49]" />
                Filter Riwayat
              </h3>
              <button
                onClick={() => setFilterModalOpen(false)}
                className="p-1 rounded-full hover:bg-[#eff4ff]"
              >
                <X className="w-4 h-4 text-[#6c7a71]" />
              </button>
            </div>

            <div>
              <label className="text-xs font-semibold text-[#3c4a42] uppercase tracking-wider block mb-1.5">
                Cari Tanggal / PPM
              </label>
              <input
                type="text"
                placeholder="Contoh: 12 Okt atau 850..."
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
                className="w-full h-11 px-3 rounded-xl border border-[#bbcabf] bg-[#f8f9ff] text-sm focus:border-[#006c49] focus:outline-none"
              />
            </div>

            <div>
              <label className="text-xs font-semibold text-[#3c4a42] uppercase tracking-wider block mb-1.5">
                Filter Status
              </label>
              <div className="grid grid-cols-2 gap-2 text-xs">
                <button
                  onClick={() => setSpecificStatusFilter('all')}
                  className={`p-2 rounded-lg border text-center font-medium ${
                    specificStatusFilter === 'all'
                      ? 'bg-[#006c49] text-white border-[#006c49]'
                      : 'border-[#bbcabf]/50 hover:bg-[#eff4ff]'
                  }`}
                >
                  Semua Status
                </button>
                <button
                  onClick={() => setSpecificStatusFilter('valid')}
                  className={`p-2 rounded-lg border text-center font-medium ${
                    specificStatusFilter === 'valid'
                      ? 'bg-[#10b981] text-[#00422b] font-bold border-[#10b981]'
                      : 'border-[#bbcabf]/50 hover:bg-[#eff4ff]'
                  }`}
                >
                  Sudah Valid
                </button>
                <button
                  onClick={() => setSpecificStatusFilter('late')}
                  className={`p-2 rounded-lg border text-center font-medium ${
                    specificStatusFilter === 'late'
                      ? 'bg-[#ffdad6] text-[#ba1a1a] font-bold border-[#ba1a1a]'
                      : 'border-[#bbcabf]/50 hover:bg-[#eff4ff]'
                  }`}
                >
                  Terlambat
                </button>
                <button
                  onClick={() => setSpecificStatusFilter('low')}
                  className={`p-2 rounded-lg border text-center font-medium ${
                    specificStatusFilter === 'low'
                      ? 'bg-[#fef08a] text-[#854d0e] font-bold border-[#ca8a04]'
                      : 'border-[#bbcabf]/50 hover:bg-[#eff4ff]'
                  }`}
                >
                  PPM Rendah
                </button>
                <button
                  onClick={() => setSpecificStatusFilter('high')}
                  className={`p-2 rounded-lg border text-center font-medium col-span-2 ${
                    specificStatusFilter === 'high'
                      ? 'bg-[#fed7aa] text-[#9a3412] font-bold border-[#ea580c]'
                      : 'border-[#bbcabf]/50 hover:bg-[#eff4ff]'
                  }`}
                >
                  PPM Tinggi
                </button>
              </div>
            </div>

            <div className="pt-2 flex gap-2">
              <button
                onClick={() => {
                  setSearchQuery('');
                  setSpecificStatusFilter('all');
                  setActiveFilter('all');
                  setFilterModalOpen(false);
                }}
                className="flex-1 py-2.5 rounded-xl border border-[#bbcabf] text-xs font-semibold text-[#3c4a42] hover:bg-[#eff4ff]"
              >
                Reset
              </button>
              <button
                onClick={() => setFilterModalOpen(false)}
                className="flex-1 py-2.5 rounded-xl bg-[#006c49] text-white text-xs font-semibold hover:bg-[#005236]"
              >
                Terapkan
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
