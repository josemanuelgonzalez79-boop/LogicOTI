export type CameraFloorCode = 'PB' | 'P1' | 'P2' | 'EXT';

export interface CameraItem {
  id: number;
  code: string;
  channelNumber: number;
  name: string;
  floorCode: CameraFloorCode;
  areaCode: string | null;
  sourceName: string;
  streamKey: string;
  active: boolean;
  videoAvailable: boolean;
  viewUrl: string | null;
}

export interface CameraListResponse {
  items: CameraItem[];
  total: number;
  playbackConfigured: boolean;
  timestamp: string;
}
