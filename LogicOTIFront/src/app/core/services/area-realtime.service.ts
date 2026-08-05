import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';

import { API_ENDPOINTS } from '../http/api.endpoints';
import { getWebsocketUrl } from '../http/realtime-url';
import { AreaState } from '../models/area-state.model';
import { AuthService } from './auth.service';
import { RealtimeConnectionStatus } from './smoke-alert-realtime.service';

@Injectable({ providedIn: 'root' })
export class AreaRealtimeService {
  private readonly authService = inject(AuthService);

  private readonly stateSubject = new Subject<AreaState>();
  private readonly connectionStatusSubject = new BehaviorSubject<RealtimeConnectionStatus>(
    'DISCONNECTED',
  );

  private client: Client | null = null;
  private subscription: StompSubscription | null = null;
  private selectedAreaCode = '';

  readonly states$: Observable<AreaState> = this.stateSubject.asObservable();

  readonly connectionStatus$: Observable<RealtimeConnectionStatus> =
    this.connectionStatusSubject.asObservable();

  watchArea(areaCode: string): void {
    const normalizedAreaCode = areaCode.trim();

    if (!normalizedAreaCode) {
      this.clearArea();
      return;
    }

    if (this.selectedAreaCode === normalizedAreaCode && this.subscription) {
      return;
    }

    this.selectedAreaCode = normalizedAreaCode;
    this.subscription?.unsubscribe();
    this.subscription = null;

    if (this.client?.connected) {
      this.subscribeToSelectedArea(this.client);
      return;
    }

    this.connect();
  }

  clearArea(): void {
    this.selectedAreaCode = '';
    this.subscription?.unsubscribe();
    this.subscription = null;
  }

  async disconnect(): Promise<void> {
    this.clearArea();

    const client = this.client;
    this.client = null;

    if (client?.active) {
      await client.deactivate();
    }

    this.connectionStatusSubject.next('DISCONNECTED');
  }

  private connect(): void {
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
      this.subscribeToSelectedArea(client);
    };

    client.onStompError = (frame) => {
      console.error('Error STOMP del estado del área:', frame.headers, frame.body);
      this.connectionStatusSubject.next('ERROR');
    };

    client.onWebSocketError = (error) => {
      console.error('Error WebSocket del estado del área:', error);
      this.connectionStatusSubject.next('ERROR');
    };

    client.onWebSocketClose = () => {
      this.subscription = null;
      this.connectionStatusSubject.next(client.active ? 'RECONNECTING' : 'DISCONNECTED');
    };

    this.client = client;
    client.activate();
  }

  private subscribeToSelectedArea(client: Client): void {
    this.subscription?.unsubscribe();
    this.subscription = null;

    if (!client.connected || !this.selectedAreaCode) {
      return;
    }

    const areaCode = this.selectedAreaCode;

    this.subscription = client.subscribe(API_ENDPOINTS.realtime.areaState(areaCode), (message) =>
      this.processMessage(message, areaCode),
    );
  }

  private processMessage(message: IMessage, expectedAreaCode: string): void {
    try {
      const state = JSON.parse(message.body) as AreaState;

      if (state.areaCode !== expectedAreaCode || state.areaCode !== this.selectedAreaCode) {
        return;
      }

      this.stateSubject.next(state);
    } catch (error) {
      console.error('El estado del área no contiene un JSON válido.', error);
    }
  }
}
