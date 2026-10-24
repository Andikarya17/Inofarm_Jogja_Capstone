import React, { useState } from 'react';
import { Bell, Code2, Smartphone, Monitor } from 'lucide-react';

interface TopAppBarProps {
  unreadCount?: number;
  onOpenNotifications: () => UnitOrVoid;
  onOpenKotlinModal: () => UnitOrVoid;
  isMobileDeviceView: boolean;
  onToggleDeviceView: () => UnitOrVoid;
}

type UnitOrVoid = void;

export const TopAppBar: React.FC<TopAppBarProps> = ({
  unreadCount = 2,
  onOpenNotifications,
  onOpenKotlinModal,
  isMobileDeviceView,
  onToggleDeviceView,
}) => {
  return (
    <header className="sticky top-0 w-full z-40 bg-[#f8f9ff]/90 backdrop-blur-md border-b border-[#bbcabf]/40 shadow-xs">
      <div className="flex items-center justify-between px-4 h-16 w-full max-w-7xl mx-auto">
        {/* Brand / Logo */}
        <div className="flex items-center gap-2.5">
          <div className="w-9 h-9 rounded-xl bg-[#006c49]/10 text-[#006c49] flex items-center justify-center">
            <span className="material-symbols-outlined fill text-2xl text-[#006c49]">potted_plant</span>
          </div>
          <div>
            <h1 className="text-xl font-bold tracking-tight text-[#006c49] leading-tight flex items-center gap-2">
              Inofarm Jogja
            </h1>
            <p className="text-[10px] text-[#3c4a42] hidden sm:block font-medium">
              Sistem Hidroponik Melon Pintar
            </p>
          </div>
        </div>

        {/* Action Buttons: Kotlin Code Switcher, View Switcher & Notification Bell */}
        <div className="flex items-center gap-2">
          {/* Kotlin Code Button */}
          <button
            onClick={onOpenKotlinModal}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold bg-[#006c49]/10 text-[#006c49] hover:bg-[#006c49] hover:text-white transition-all active:scale-95 border border-[#006c49]/20 shadow-xs"
            title="Lihat Kode Kotlin (Jetpack Compose)"
          >
            <Code2 className="w-3.5 h-3.5" />
            <span className="hidden xs:inline">Kode Kotlin</span>
          </button>

          {/* Device Frame View Toggle */}
          <button
            onClick={onToggleDeviceView}
            className="p-2 rounded-full text-[#3c4a42] hover:bg-[#eff4ff] transition-colors border border-[#bbcabf]/30 active:scale-95"
            title={isMobileDeviceView ? "Tampilan Fullscreen" : "Tampilan Mockup Mobile"}
          >
            {isMobileDeviceView ? (
              <Monitor className="w-4 h-4" />
            ) : (
              <Smartphone className="w-4 h-4 text-[#006c49]" />
            )}
          </button>

          {/* Notification Bell */}
          <button
            onClick={onOpenNotifications}
            className="relative p-2 rounded-full text-[#3c4a42] hover:bg-[#eff4ff] transition-colors active:scale-95 duration-150"
            aria-label="Notifikasi"
          >
            <Bell className="w-5 h-5 text-[#3c4a42]" />
            {unreadCount > 0 && (
              <span className="absolute top-1.5 right-1.5 w-2.5 h-2.5 bg-[#ba1a1a] rounded-full ring-2 ring-white animate-pulse" />
            )}
          </button>
        </div>
      </div>
    </header>
  );
};
