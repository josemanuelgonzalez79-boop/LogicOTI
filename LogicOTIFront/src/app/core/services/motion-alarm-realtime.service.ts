import { Injectable, inject } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';

import { API_ENDPOINTS } from '../http/api.endpoints';
import { getWebsocketUrl } from '../http/realtime-url';
import { MotionAlarmAlert } from '../models/motion-alarm.model';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class MotionAlarmRealtimeService {
  private readonly authService = inject(AuthService);
  private readonly alertSubject = new Subject<MotionAlarmAlert>();

  private client: Client | null = null;
  private subscription: StompSubscription | null = null;

  readonly alerts$: Observable<MotionAlarmAlert> = this.alertSubject.asObservable();

  connect(): void {
    if (this.client?.active) {
      return;
    }

    const token = this.authService.getToken();

    if (!token) {
      return;
    }

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

    client.onConnect = () => this.subscribe(client);

    client.onStompError = (frame) => {
      console.error('Error STOMP de alarmas de movimiento:', frame.headers, frame.body);
    };

    client.onWebSocketError = (error) => {
      console.error('Error WebSocket de alarmas de movimiento:', error);
    };

    client.onWebSocketClose = () => {
      this.subscription = null;
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
  }

  private subscribe(client: Client): void {
    this.subscription?.unsubscribe();
    this.subscription = client.subscribe(API_ENDPOINTS.realtime.securityMotionAlerts, (message) =>
      this.processMessage(message),
    );
  }

  private processMessage(message: IMessage): void {
    try {
      const alert = JSON.parse(message.body) as MotionAlarmAlert;

      if (alert.event.deviceType === 'MOTION' && alert.event.currentState) {
        this.alertSubject.next(alert);
      }
    } catch (error) {
      console.error('La alarma de movimiento no contiene un JSON válido.', error);
    }
  }
}
