export type AlarmMode =
  'DISARMED' | 'ARMING' | 'ARMED' | 'ARMED_WITH_BYPASS' | 'REJECTED' | 'ALARM';

export interface SecurityStatus {
  mode: AlarmMode;
  armed: boolean;
  alarmActive: boolean;
  message: string;
  changedBy: string;
  changeSource: 'MANUAL' | 'SCHEDULE' | 'SYSTEM';
  changedAt: string;
  armingCompletesAt: string | null;
  automaticScheduleEnabled: boolean;
  timezone: string;
  exitDelaySeconds: number;
  lightInactivityMinutes: number;
  minisplitInactivityMinutes: number;
  timestamp: string;
}

export interface SecurityPrecheckIssue {
  code: string;
  severity: string;
  blocking: boolean;
  sensorCode: string | null;
  sensorName: string | null;
  areaCode: string | null;
  areaName: string | null;
  message: string;
}

export interface SecurityPrecheck {
  ready: boolean;
  plcEnabled: boolean;
  plcConnected: boolean;
  totalMotionSensors: number;
  readableMotionSensors: number;
  issues: SecurityPrecheckIssue[];
  timestamp: string;
}

export interface SecurityActionResponse {
  status: SecurityStatus;
  precheck: SecurityPrecheck | null;
}

export interface SecurityScheduleDay {
  dayOfWeek: number;
  dayName: string;
  enabled: boolean;
  allDayArmed: boolean;
  armTime: string;
  disarmTime: string;
}

export interface SecuritySettings {
  automaticScheduleEnabled: boolean;
  timezone: string;
  exitDelaySeconds: number;
  lightInactivityMinutes: number;
  minisplitInactivityMinutes: number;
  diagnosticTimeoutSeconds: number;
  diagnosticValidityMonths: number;
  days: SecurityScheduleDay[];
  updatedAt: string;
  updatedBy: string;
}

export interface SecurityScheduleDayRequest {
  dayOfWeek: number;
  enabled: boolean;
  allDayArmed: boolean;
  armTime: string;
  disarmTime: string;
}

export interface SecuritySettingsUpdateRequest {
  automaticScheduleEnabled: boolean;
  timezone: string;
  exitDelaySeconds: number;
  lightInactivityMinutes: number;
  minisplitInactivityMinutes: number;
  diagnosticTimeoutSeconds: number;
  diagnosticValidityMonths: number;
  days: SecurityScheduleDayRequest[];
}
