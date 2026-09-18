import { CameraItem } from './camera.model';
import { SensorEventHistoryItem } from './history.model';
import { SecurityStatus } from './security.model';

export interface MotionAlarmAlert {
  event: SensorEventHistoryItem;
  security: SecurityStatus;
  cameras: CameraItem[];
  message: string;
  publishedAt: string;
}
