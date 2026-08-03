export type DeviceType = 'LIGHT' | 'MOTION' | 'SMOKE' | 'MINISPLIT' | string;

export interface AreaState {
  areaCode: string;
  areaName: string;
  plcEnabled: boolean;
  connected: boolean;
  devices: AreaDevice[];
  message: string;
  timestamp: string;
}

export interface AreaDevice {
  id: number;
  code: string;
  name: string;
  type: DeviceType;
  number: number;
  controllable: boolean;
  command: boolean | null;
  state: boolean | null;
  fault: boolean | null;
}
