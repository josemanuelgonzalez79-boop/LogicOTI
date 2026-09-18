import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';

import { API_ENDPOINTS } from '../http/api.endpoints';
import { getWebsocketUrl } from '../http/realtime-url';
import { AlarmActivity, SensorEventHistoryItem } from '../models/history.model';
import { AuthService } from './auth.service';

export type RealtimeConnectionStatus =
  'DISCONNECTED' | 'CONNECTING' | 'CONNECTED' | 'RECONNECTING' | 'UNAUTHORIZED' | 'ERROR';

@Injectable({ providedIn: 'root' })
export class SmokeAlertRealtimeService {
  private readonly authService = inject(AuthService);

  private readonly alertSubject = new Subject<SensorEventHistoryItem>();
  private readonly attentionSubject = new Subject<AlarmActivity>();
  private readonly connectionStatusSubject = new BehaviorSubject<RealtimeConnectionStatus>(
    'DISCONNECTED',
  );

  private client: Client | null = null;
  private smokeSubscription: StompSubscription | null = null;
  private attentionSubscription: StompSubscription | null = null;

  readonly alerts$: Observable<SensorEventHistoryItem> = this.alertSubject.asObservable();
  readonly attention$: Observable<AlarmActivity> = this.attentionSubject.asObservable();

  readonly connectionStatus$: Observable<RealtimeConnectionStatus> =
    this.connectionStatusSubject.asObservable();

  connect(): void {
    if (this.client?.active) {
      return;
    }

    const token = this.authService.getToken();

    if (!token) {
      this.connectionStatusSubject.next('UNAUTHORIZED');
      return;
    }

    this.connectionStatusSubject.next('CONNECTING');

    const client = new Client({
      brokerURL: getWebsocketUrl(),
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => undefined,
    });

    client.onConnect = () => {
      this.connectionStatusSubject.next('CONNECTED');
      this.subscribeToSmokeAlerts(client);
      this.subscribeToAlarmAttention(client);
    };

    client.onStompError = (frame) => {
      console.error('Error STOMP de alertas:', frame.headers, frame.body);
      this.connectionStatusSubject.next('ERROR');
    };

    client.onWebSocketError = (error) => {
      console.error('Error WebSocket de alertas:', error);
      this.connectionStatusSubject.next('ERROR');
    };

    client.onWebSocketClose = () => {
      this.smokeSubscription = null;
      this.attentionSubscription = null;

      this.connectionStatusSubject.next(client.active ? 'RECONNECTING' : 'DISCONNECTED');
    };

    this.client = client;
    client.activate();
  }

  async disconnect(): Promise<void> {
    this.smokeSubscription?.unsubscribe();
    this.attentionSubscription?.unsubscribe();
    this.smokeSubscription = null;
    this.attentionSubscription = null;

    const client = this.client;
    this.client = null;

    if (client?.active) {
      await client.deactivate();
    }

    this.connectionStatusSubject.next('DISCONNECTED');
  }

  private subscribeToSmokeAlerts(client: Client): void {
    this.smokeSubscription?.unsubscribe();

    this.smokeSubscription = client.subscribe(API_ENDPOINTS.realtime.smokeAlerts, (message) =>
      this.processMessage(message),
    );
  }

  private subscribeToAlarmAttention(client: Client): void {
    this.attentionSubscription?.unsubscribe();

    this.attentionSubscription = client.subscribe(
      API_ENDPOINTS.realtime.alarmAttention,
      (message) => this.processAttentionMessage(message),
    );
  }

  private processMessage(message: IMessage): void {
    try {
      const event = JSON.parse(message.body) as SensorEventHistoryItem;

      if (event.deviceType !== 'SMOKE') {
        return;
      }

      this.alertSubject.next(event);
    } catch (error) {
      console.error('La alerta de humo no contiene un JSON válido.', error);
    }
  }

  private processAttentionMessage(message: IMessage): void {
    try {
      const activity = JSON.parse(message.body) as AlarmActivity;

      if (!Number.isInteger(activity.eventId)) {
        return;
      }

      this.attentionSubject.next(activity);
    } catch (error) {
      console.error('La atención de alarma no contiene un JSON válido.', error);
    }
  }
}
