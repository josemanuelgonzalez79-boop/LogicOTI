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
  ptzAvailable: boolean;
}

export type CameraPtzDirection = 'UP' | 'DOWN' | 'LEFT' | 'RIGHT' | 'ZOOM_IN' | 'ZOOM_OUT';

export interface CameraListResponse {
  items: CameraItem[];
  total: number;
  playbackConfigured: boolean;
  historyConfigured: boolean;
  timestamp: string;
}

export interface CameraRecordingSearchRequest {
  startTime: string;
  endTime: string;
}

export interface CameraRecordingSegment {
  sequence: number;
  startTime: string;
  endTime: string;
  codecType: string;
  recordingType: string;
}

export interface CameraRecordingSearchResponse {
  cameraCode: string;
  cameraName: string;
  channelNumber: number;
  requestedStartTime: string;
  requestedEndTime: string;
  items: CameraRecordingSegment[];
  total: number;
  playbackConfigured: boolean;
  timestamp: string;
}

export interface CameraRecordingPlaybackRequest {
  startTime: string;
  endTime: string;
}

export interface CameraRecordingPlaybackResponse {
  cameraCode: string;
  cameraName: string;
  viewUrl: string;
  expiresAt: string;
  timestamp: string;
}

export interface CameraAlertView {
  cameraCode: string;
  cameraName: string;
  floorCode: CameraFloorCode;
  areaCode: string | null;
  viewUrl: string;
  expiresAt: string;
  timestamp: string;
}
