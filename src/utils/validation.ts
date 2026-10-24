import { NutrientLog, NutrientLogStatus, PlantPhase, ValidationSettings } from '../types';

export const DEFAULT_SETTINGS: ValidationSettings = {
  morningSchedule: '08:00',
  afternoonSchedule: '16:00',
  toleranceMinutes: 15,
  activePhase: 'vegetatif',
  ppmRanges: {
    vegetatif: { min: 800, max: 1000 },
    generatif: { min: 1100, max: 1400 },
  },
  sensorThresholds: {
    waterTemp: { min: 20, max: 28 },
    ph: { min: 5.5, max: 6.5 },
  },
};

export const INITIAL_LOGS: NutrientLog[] = [
  {
    id: 'log-1',
    dateStr: '12 Okt 2026',
    timeStr: '16:05 WIB',
    timestamp: new Date('2026-10-12T16:05:00').getTime(),
    ppm: 860,
    phase: 'vegetatif',
    status: 'valid',
    statusLabel: 'Sudah Tervalidasi',
    notes: 'Kondisi larutan nutrisi stabil, diserap baik oleh akar melon.',
    recordedBy: 'Siti Rahma (Operator)',
  },
  {
    id: 'log-2',
    dateStr: '12 Okt 2026',
    timeStr: '08:45 WIB',
    timestamp: new Date('2026-10-12T08:45:00').getTime(),
    ppm: 850,
    phase: 'vegetatif',
    status: 'late',
    statusLabel: 'Terlambat Dicatat',
    notes: 'Pencatatan melewati batas toleransi 15 menit (tercatat pukul 08:45 WIB).',
    recordedBy: 'Budi Santoso (Teknisi)',
  },
  {
    id: 'log-3',
    dateStr: '11 Okt 2026',
    timeStr: '16:00 WIB',
    timestamp: new Date('2026-10-11T16:00:00').getTime(),
    ppm: 400,
    phase: 'vegetatif',
    status: 'low',
    statusLabel: 'PPM Rendah',
    notes: 'Konsentrasi PPM di bawah batas minimal (400 PPM < 800 PPM). Perlu penambahan AB Mix.',
    recordedBy: 'Siti Rahma (Operator)',
  },
  {
    id: 'log-4',
    dateStr: '11 Okt 2026',
    timeStr: '08:00 WIB',
    timestamp: new Date('2026-10-11T08:00:00').getTime(),
    ppm: 1200,
    phase: 'vegetatif',
    status: 'high',
    statusLabel: 'PPM Tinggi',
    notes: 'Konsentrasi PPM melampaui batas fase vegetatif (1200 PPM > 1000 PPM). Segera encerkan dengan air baku.',
    recordedBy: 'Budi Santoso (Teknisi)',
  },
  {
    id: 'log-5',
    dateStr: '10 Okt 2026',
    timeStr: '16:10 WIB',
    timestamp: new Date('2026-10-10T16:10:00').getTime(),
    ppm: 890,
    phase: 'vegetatif',
    status: 'valid',
    statusLabel: 'Sudah Tervalidasi',
    notes: 'Pengecekan rutin sore hari dalam parameter normal.',
    recordedBy: 'Siti Rahma (Operator)',
  },
  {
    id: 'log-6',
    dateStr: '10 Okt 2026',
    timeStr: '08:05 WIB',
    timestamp: new Date('2026-10-10T08:05:00').getTime(),
    ppm: 875,
    phase: 'vegetatif',
    status: 'valid',
    statusLabel: 'Sudah Tervalidasi',
    notes: 'Suhu tandon sejuk, sirkulasi pompa lancar.',
    recordedBy: 'Agus Pratama (Agronomis)',
  },
];

export function validateNutrientInput(
  timeStr: string, // "HH:mm"
  ppm: number,
  phase: PlantPhase,
  settings: ValidationSettings
): { status: NutrientLogStatus; statusLabel: string; reason: string } {
  const [hours, minutes] = timeStr.split(':').map(Number);
  const totalMinutes = hours * 60 + minutes;

  const [mH, mM] = settings.morningSchedule.split(':').map(Number);
  const morningMinutes = mH * 60 + mM;

  const [aH, aM] = settings.afternoonSchedule.split(':').map(Number);
  const afternoonMinutes = aH * 60 + aM;

  const morningDiff = Math.abs(totalMinutes - morningMinutes);
  const afternoonDiff = Math.abs(totalMinutes - afternoonMinutes);
  const minDiff = Math.min(morningDiff, afternoonDiff);

  const isTimeValid = minDiff <= settings.toleranceMinutes;

  const currentRange = settings.ppmRanges[phase];
  const isPpmLow = ppm < currentRange.min;
  const isPpmHigh = ppm > currentRange.max;

  if (isPpmLow) {
    return {
      status: 'low',
      statusLabel: 'PPM Rendah',
      reason: `Nilai PPM (${ppm}) berada di bawah batas minimum fase ${phase} (${currentRange.min} PPM).`,
    };
  }

  if (isPpmHigh) {
    return {
      status: 'high',
      statusLabel: 'PPM Tinggi',
      reason: `Nilai PPM (${ppm}) melampaui batas maksimum fase ${phase} (${currentRange.max} PPM).`,
    };
  }

  if (!isTimeValid) {
    return {
      status: 'late',
      statusLabel: 'Terlambat Dicatat',
      reason: `Waktu input (${timeStr}) melewati jadwal standar (${settings.morningSchedule} / ${settings.afternoonSchedule}) dengan toleransi ${settings.toleranceMinutes} menit.`,
    };
  }

  return {
    status: 'valid',
    statusLabel: 'Sudah Tervalidasi',
    reason: `Nilai PPM (${ppm}) dan waktu input tepat waktu serta sesuai ambang batas.`,
  };
}
