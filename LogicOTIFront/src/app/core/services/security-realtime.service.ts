import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';

import { API_ENDPOINTS } from '../http/api.endpoints';
import { getWebsocketUrl } from '../http/realtime-url';
import { AutomaticLightingStatus, SecurityStatus } from '../models/security.model';
import { AuthService } from './auth.service';
import { RealtimeConnectionStatus } from './smoke-alert-realtime.service';

@Injectable({ providedIn: 'root' })
export class SecurityRealtimeService {
  private readonly authService = inject(AuthService);

  private readonly statusSubject = new Subject<SecurityStatus>();
  private readonly automaticLightingSubject = new Subject<AutomaticLightingStatus>();
  private readonly connectionStatusSubject = new BehaviorSubject<RealtimeConnectionStatus>(
    'DISCONNECTED',
  );

  private client: Client | null = null;
  private subscription: StompSubscription | null = null;
  private automaticLightingSubscription: StompSubscription | null = null;

  readonly status$: Observable<SecurityStatus> = this.statusSubject.asObservable();
  readonly automaticLighting$: Observable<AutomaticLightingStatus> =
    this.automaticLightingSubject.asObservable();
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
      this.subscribe(client);
    };

    client.onStompError = (frame) => {
      console.error('Error STOMP de seguridad:', frame.headers, frame.body);
      this.connectionStatusSubject.next('ERROR');
    };

    client.onWebSocketError = (error) => {
      console.error('Error WebSocket de seguridad:', error);
      this.connectionStatusSubject.next('ERROR');
    };

    client.onWebSocketClose = () => {
      this.subscription = null;
      this.automaticLightingSubscription = null;
      this.connectionStatusSubject.next(client.active ? 'RECONNECTING' : 'DISCONNECTED');
    };

    this.client = client;
    client.activate();
  }

  async disconnect(): Promise<void> {
    this.subscription?.unsubscribe();
    this.subscription = null;
    this.automaticLightingSubscription?.unsubscribe();
    this.automaticLightingSubscription = null;

    const client = this.client;
    this.client = null;

    if (client?.active) {
      await client.deactivate();
    }

    this.connectionStatusSubject.next('DISCONNECTED');
  }

  private subscribe(client: Client): void {
    this.subscription?.unsubscribe();
    this.automaticLightingSubscription?.unsubscribe();
    this.subscription = client.subscribe(API_ENDPOINTS.realtime.securityStatus, (message) =>
      this.processStatus(message),
    );
    this.automaticLightingSubscription = client.subscribe(
      API_ENDPOINTS.realtime.automaticLighting,
      (message) => this.processAutomaticLighting(message),
    );
  }

  private processStatus(message: IMessage): void {
    try {
      this.statusSubject.next(JSON.parse(message.body) as SecurityStatus);
    } catch (error) {
      console.error('El estado de seguridad recibido no contiene un JSON válido.', error);
    }
  }

  private processAutomaticLighting(message: IMessage): void {
    try {
      this.automaticLightingSubject.next(JSON.parse(message.body) as AutomaticLightingStatus);
    } catch (error) {
      console.error('El estado de iluminación automática no contiene un JSON válido.', error);
    }
  }
}
