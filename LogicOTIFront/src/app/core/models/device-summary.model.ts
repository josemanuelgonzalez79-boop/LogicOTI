export interface DeviceSummary {
  plcEnabled: boolean;
  connected: boolean;
  totalControllable: number;
  poweredOn: number | null;
  lightsOn: number | null;
  minisplitsOn: number | null;
  message: string;
  timestamp: string;
}
