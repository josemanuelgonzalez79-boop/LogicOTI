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

export interface ActiveAlarmList {
  items: SensorEventHistoryItem[];
  total: number;
  timestamp: string;
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
  acknowledged: boolean;
  acknowledgedBy: string | null;
  acknowledgedAt: string | null;
  commentCount: number;
}

export interface AlarmAcknowledgement {
  id: number;
  eventId: number;
  acknowledgedBy: string;
  acknowledgedAt: string;
}

export interface AlarmComment {
  id: number;
  eventId: number;
  comment: string;
  createdBy: string;
  createdAt: string;
}

export interface AlarmActivity {
  eventId: number;
  acknowledgement: AlarmAcknowledgement | null;
  comments: AlarmComment[];
  commentCount: number;
  timestamp: string;
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

export interface ExecutiveCountMetric {
  code: string;
  label: string;
  count: number;
}

export interface ExecutiveDailyMetric {
  date: string;
  count: number;
}

export interface ExecutiveDeviceFailureMetric {
  deviceCode: string;
  deviceName: string;
  areaCode: string;
  areaName: string;
  failures: number;
}

export interface ExecutiveMonthlyReport {
  month: string;
  periodStart: string;
  periodEnd: string;
  timezone: string;
  generatedAt: string;
  alarms: {
    total: number;
    smoke: number;
    motion: number;
    critical: number;
    acknowledged: number;
    acknowledgementRate: number;
    averageRestoreMinutes: number | null;
  };
  commands: {
    total: number;
    confirmed: number;
    failed: number;
    pending: number;
    confirmationRate: number;
    averageLatencyMs: number | null;
  };
  maintenance: {
    diagnosticsPassed: number;
    diagnosticsRejected: number;
    diagnosticsCancelled: number;
    bypassesCreated: number;
  };
  security: {
    rejectedArmings: number;
  };
  alarmsByArea: ExecutiveCountMetric[];
  alarmsBySensor: ExecutiveCountMetric[];
  alarmsByDay: ExecutiveDailyMetric[];
  alarmsByTimeSlot: ExecutiveCountMetric[];
  commandFailures: ExecutiveDeviceFailureMetric[];
  armRejectionReasons: ExecutiveCountMetric[];
}

export interface HistoryRetentionCounts {
  events: number;
  commands: number;
  securityTransitions: number;
  diagnostics: number;
  revokedBypasses: number;
  notifications: number;
  total: number;
}

export interface HistoryRetentionPolicy {
  enabled: boolean;
  retentionMonths: number;
  cutoffAt: string;
  candidates: HistoryRetentionCounts;
  lastRunAt: string | null;
  lastRunBy: string | null;
  lastCutoffAt: string | null;
  lastDeleted: HistoryRetentionCounts;
  updatedAt: string;
  updatedBy: string;
  timestamp: string;
}

export interface HistoryRetentionRunResponse {
  cutoffAt: string;
  deleted: HistoryRetentionCounts;
  executedAt: string;
  executedBy: string;
}
