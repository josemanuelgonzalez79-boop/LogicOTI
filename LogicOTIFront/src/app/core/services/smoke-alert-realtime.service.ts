import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';

import { environment } from '../../../environments/environment';
import { API_ENDPOINTS } from '../http/api.endpoints';
import { SensorEventHistoryItem } from '../models/history.model';
import { AuthService } from './auth.service';

export type RealtimeConnectionStatus =
  'DISCONNECTED' | 'CONNECTING' | 'CONNECTED' | 'RECONNECTING' | 'UNAUTHORIZED' | 'ERROR';

@Injectable({ providedIn: 'root' })
export class SmokeAlertRealtimeService {
  private readonly authService = inject(AuthService);

  private readonly alertSubject = new Subject<SensorEventHistoryItem>();
  private readonly connectionStatusSubject = new BehaviorSubject<RealtimeConnectionStatus>(
    'DISCONNECTED',
  );

  private client: Client | null = null;
  private subscription: StompSubscription | null = null;

  readonly alerts$: Observable<SensorEventHistoryItem> = this.alertSubject.asObservable();

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
      brokerURL: environment.websocketUrl,
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
      this.subscription = null;

      this.connectionStatusSubject.next(client.active ? 'RECONNECTING' : 'DISCONNECTED');
    };

    this.client = client;
    client.activate();
  }

  async disconnect(): Promise<void> {
    this.subscription?.unsubscribe();
    this.subscription = null;

    const client = this.client;
    this.client = null;

    if (client?.active) {
      await client.deactivate();
    }

    this.connectionStatusSubject.next('DISCONNECTED');
  }

  private subscribeToSmokeAlerts(client: Client): void {
    this.subscription?.unsubscribe();

    this.subscription = client.subscribe(API_ENDPOINTS.realtime.smokeAlerts, (message) =>
      this.processMessage(message),
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
}
