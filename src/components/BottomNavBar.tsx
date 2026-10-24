import React from 'react';
import { TabKey } from '../types';

interface BottomNavBarProps {
  currentTab: TabKey;
  onSelectTab: (tab: TabKey) => void;
}

export const BottomNavBar: React.FC<BottomNavBarProps> = ({
  currentTab,
  onSelectTab,
}) => {
  return (
    <nav className="fixed bottom-0 left-0 right-0 z-40 bg-[#f8f9ff]/95 backdrop-blur-md border-t border-[#bbcabf]/50 shadow-lg pb-safe">
      <div className="flex justify-around items-center w-full max-w-lg mx-auto h-18 px-3">
        {/* Tab 1: Beranda */}
        <button
          onClick={() => onSelectTab('beranda')}
          className={`flex flex-col items-center justify-center transition-all duration-200 active:scale-95 ${
            currentTab === 'beranda'
              ? 'bg-[#10b981] text-[#00422b] font-bold px-6 py-1.5 rounded-full shadow-xs'
              : 'text-[#3c4a42] hover:text-[#006c49] px-4 py-1.5 rounded-full'
          }`}
        >
          <span
            className={`material-symbols-outlined text-[24px] ${
              currentTab === 'beranda' ? 'fill text-[#00422b]' : ''
            }`}
          >
            home
          </span>
          <span className="text-xs tracking-tight mt-0.5">Beranda</span>
        </button>

        {/* Tab 2: Riwayat */}
        <button
          onClick={() => onSelectTab('riwayat')}
          className={`flex flex-col items-center justify-center transition-all duration-200 active:scale-95 ${
            currentTab === 'riwayat'
              ? 'bg-[#10b981] text-[#00422b] font-bold px-6 py-1.5 rounded-full shadow-xs'
              : 'text-[#3c4a42] hover:text-[#006c49] px-4 py-1.5 rounded-full'
          }`}
        >
          <span
            className={`material-symbols-outlined text-[24px] ${
              currentTab === 'riwayat' ? 'fill text-[#00422b]' : ''
            }`}
          >
            history
          </span>
          <span className="text-xs tracking-tight mt-0.5">Riwayat</span>
        </button>

        {/* Tab 3: Pengaturan */}
        <button
          onClick={() => onSelectTab('pengaturan')}
          className={`flex flex-col items-center justify-center transition-all duration-200 active:scale-95 ${
            currentTab === 'pengaturan'
              ? 'bg-[#10b981] text-[#00422b] font-bold px-6 py-1.5 rounded-full shadow-xs'
              : 'text-[#3c4a42] hover:text-[#006c49] px-4 py-1.5 rounded-full'
          }`}
        >
          <span
            className={`material-symbols-outlined text-[24px] ${
              currentTab === 'pengaturan' ? 'fill text-[#00422b]' : ''
            }`}
          >
            settings
          </span>
          <span className="text-xs tracking-tight mt-0.5">Pengaturan</span>
        </button>
      </div>
    </nav>
  );
};
