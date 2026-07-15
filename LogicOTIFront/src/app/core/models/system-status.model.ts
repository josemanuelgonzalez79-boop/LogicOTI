export interface SystemStatus {
  application: string;
  status: 'UP' | 'DEGRADED';
  database: 'UP' | 'DOWN';
  plcEnabled: boolean;
  timestamp: string;
}
