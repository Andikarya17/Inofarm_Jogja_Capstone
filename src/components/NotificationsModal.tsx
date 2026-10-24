import React from 'react';
import { X, Bell, CheckCircle, AlertTriangle, Cpu } from 'lucide-react';

interface NotificationsModalProps {
  isOpen: boolean;
  onClose: () => void;
  onClearAll: () => void;
}

export const NotificationsModal: React.FC<NotificationsModalProps> = ({
  isOpen,
  onClose,
  onClearAll,
}) => {
  if (!isOpen) return null;

  const notificationList = [
    {
      id: 1,
      title: 'Pencatatan Nutrisi Tervalidasi',
      desc: 'Pencatatan manual 850 PPM pukul 08:05 WIB telah masuk kriteria ideal fase vegetatif.',
      time: '15 menit lalu',
      type: 'success',
      icon: CheckCircle,
      color: 'text-[#006c49]',
      bg: 'bg-[#10b981]/15',
    },
    {
      id: 2,
      title: 'Perangkat IoT Aktif & Stabil',
      desc: 'Koneksi telemetri sensor Suhu Air (24.5°C) dan pH (6.2) berjalan normal.',
      time: '1 jam lalu',
      type: 'info',
      icon: Cpu,
      color: 'text-[#0058be]',
      bg: 'bg-[#eff4ff]',
    },
    {
      id: 3,
      title: 'Pengingat Jadwal Nutrisi Sore',
      desc: 'Jadwal input sore pukul 16:00 WIB (Toleransi 15 menit). Siapkan alat ukur TDS/EC.',
      time: '3 jam lalu',
      type: 'warning',
      icon: AlertTriangle,
      color: 'text-[#9a3412]',
      bg: 'bg-[#fed7aa]/50',
    },
  ];

  return (
    <div className="fixed inset-0 z-50 bg-black/40 backdrop-blur-xs flex items-center justify-center p-4">
      <div className="bg-white w-full max-w-sm rounded-3xl p-5 shadow-2xl border border-[#bbcabf]/50 space-y-4">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-[#bbcabf]/30 pb-3">
          <div className="flex items-center gap-2">
            <div className="p-2 bg-[#eff4ff] text-[#006c49] rounded-xl">
              <Bell className="w-4 h-4 text-[#006c49]" />
            </div>
            <h3 className="text-base font-bold text-[#121c2a]">Notifikasi Sistem</h3>
          </div>
          <button
            onClick={onClose}
            className="p-1 rounded-full hover:bg-[#eff4ff] text-[#6c7a71]"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* List */}
        <div className="space-y-2.5 max-h-80 overflow-y-auto pr-1">
          {notificationList.map(item => {
            const Icon = item.icon;
            return (
              <div
                key={item.id}
                className="p-3 rounded-2xl bg-[#f8f9ff] border border-[#bbcabf]/40 flex gap-3 items-start hover:bg-[#dee9fc]/50 transition-colors"
              >
                <div className={`p-2 rounded-xl ${item.bg} ${item.color} shrink-0 mt-0.5`}>
                  <Icon className="w-4 h-4" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between gap-1">
                    <p className="text-xs font-bold text-[#121c2a] truncate">
                      {item.title}
                    </p>
                    <span className="text-[10px] text-[#6c7a71] shrink-0">
                      {item.time}
                    </span>
                  </div>
                  <p className="text-[11px] text-[#3c4a42] mt-0.5 leading-snug">
                    {item.desc}
                  </p>
                </div>
              </div>
            );
          })}
        </div>

        {/* Action footer */}
        <div className="pt-2 flex gap-2">
          <button
            onClick={() => {
              onClearAll();
              onClose();
            }}
            className="w-full py-2.5 rounded-xl bg-[#006c49] text-white text-xs font-bold hover:bg-[#005236] transition-colors"
          >
            Tandai Semua Sudah Dibaca
          </button>
        </div>
      </div>
    </div>
  );
};
