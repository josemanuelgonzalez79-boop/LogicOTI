export type SensorType = 'MOTION' | 'SMOKE';

export type SensorDueStatus = 'VALID' | 'DUE' | 'EXPIRED';

export type DiagnosticStatus = 'RUNNING' | 'PASSED' | 'REJECTED' | 'CANCELLED';

export interface SensorDiagnosticDueItem {
  deviceCode: string;
  deviceName: string;
  areaCode: string;
  areaName: string;
  deviceType: SensorType;
  status: SensorDueStatus;
  lastPassedAt: string | null;
  validUntil: string | null;
}

export interface SensorDiagnosticDueResponse {
  validityMonths: number;
  totalSensors: number;
  dueSensors: number;
  sensors: SensorDiagnosticDueItem[];
  timestamp: string;
}

export interface SensorDiagnosticItem {
  id: number;
  deviceCode: string;
  deviceName: string;
  areaCode: string;
  areaName: string;
  deviceType: SensorType;
  plcStateTag: string;
  initialState: boolean;
  sawInactive: boolean;
  sawActive: boolean;
  status: DiagnosticStatus;
  passedAt: string | null;
}

export interface SensorDiagnostic {
  id: number;
  status: DiagnosticStatus;
  startedBy: string;
  startedByRole: string;
  startedAt: string;
  expiresAt: string;
  completedAt: string | null;
  remainingSeconds: number;
  message: string;
  sensors: SensorDiagnosticItem[];
}

export interface SensorDiagnosticListResponse {
  items: SensorDiagnostic[];
  total: number;
  timestamp: string;
}

export interface SensorBypass {
  id: number;
  sensorCode: string;
  sensorName: string;
  areaCode: string;
  areaName: string;
  reason: string;
  active: boolean;
  createdBy: string;
  createdAt: string;
  revokedBy: string | null;
  revokedAt: string | null;
}

export interface SensorBypassListResponse {
  items: SensorBypass[];
  total: number;
  timestamp: string;
}

export interface SecurityWarning {
  id: number;
  bypassId: number;
  type: string;
  sensorCode: string;
  sensorName: string;
  areaCode: string;
  areaName: string;
  reason: string;
  message: string;
  warningDate: string;
  createdAt: string;
}

export interface SecurityWarningListResponse {
  items: SecurityWarning[];
  total: number;
  timestamp: string;
}
