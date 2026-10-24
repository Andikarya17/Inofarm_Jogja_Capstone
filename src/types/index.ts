export type PlantPhase = 'vegetatif' | 'generatif';

export type IoTStatus = 'online' | 'offline' | 'reconnecting';

export type SensorReading = {
  waterTemp: number; // e.g. 24.5 °C
  ph: number;        // e.g. 6.2
  roomTemp: number;  // e.g. 28.0 °C
  humidity: number;  // e.g. 72 %
  timestamp: string;
};

export type NutrientLogStatus = 'valid' | 'late' | 'low' | 'high';

export interface NutrientLog {
  id: string;
  dateStr: string;      // e.g. '12 Okt 2026'
  timeStr: string;      // e.g. '16:05 WIB'
  timestamp: number;
  ppm: number;          // e.g. 860
  phase: PlantPhase;
  status: NutrientLogStatus;
  statusLabel: string;  // 'Sudah Tervalidasi', 'Terlambat Dicatat', 'PPM Rendah', 'PPM Tinggi'
  notes?: string;
  recordedBy?: string;
}

export interface ValidationSettings {
  morningSchedule: string;   // '08:00'
  afternoonSchedule: string; // '16:00'
  toleranceMinutes: number;  // 15
  activePhase: PlantPhase;
  ppmRanges: {
    vegetatif: { min: number; max: number }; // 800 - 1000
    generatif: { min: number; max: number }; // 1100 - 1400
  };
  sensorThresholds: {
    waterTemp: { min: number; max: number }; // 20 - 28 °C
    ph: { min: number; max: number };        // 5.5 - 6.5
  };
}

export type TabKey = 'beranda' | 'riwayat' | 'pengaturan';
