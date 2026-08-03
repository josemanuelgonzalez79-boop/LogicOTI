export type CommandStatus = 'PENDING' | 'CONFIRMED' | 'NOT_CONFIRMED' | 'FAILED' | 'REJECTED';

export type SensorDeviceType = 'MOTION' | 'SMOKE';
export type SensorEventType = 'ACTIVATED' | 'CLEARED';
export type SensorEventSeverity = 'INFO' | 'WARNING' | 'CRITICAL';

export interface HistoryPage<T> {
  items: T[];
  total: number;
  limit: number;
  offset: number;
}

export interface CommandHistoryItem {
  id: number;
  deviceCode: string;
  deviceName: string;
  areaCode: string;
  areaName: string;
  plcCommandTag: string | null;
  requestedValue: boolean;
  commandValue: boolean | null;
  feedbackValue: boolean | null;
  status: CommandStatus;
  message: string;
  requestedBy: string;
  requestedByRole: string;
  sourceIp: string;
  durationMs: number | null;
  requestedAt: string;
  completedAt: string | null;
}

export interface SensorEventHistoryItem {
  id: number;
  deviceCode: string;
  deviceName: string;
  areaCode: string;
  areaName: string;
  deviceType: SensorDeviceType;
  plcStateTag: string;
  previousState: boolean | null;
  currentState: boolean;
  eventType: SensorEventType;
  severity: SensorEventSeverity;
  message: string;
  detectedAt: string;
}

export interface CommandHistoryFilters {
  areaCode?: string;
  deviceCode?: string;
  status?: CommandStatus;
  requestedBy?: string;
  from?: string;
  to?: string;
  limit: number;
  offset: number;
}

export interface SensorEventHistoryFilters {
  areaCode?: string;
  deviceCode?: string;
  deviceType?: SensorDeviceType;
  eventType?: SensorEventType;
  severity?: SensorEventSeverity;
  from?: string;
  to?: string;
  limit: number;
  offset: number;
}
