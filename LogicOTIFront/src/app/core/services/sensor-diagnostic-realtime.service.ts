import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';

import { API_ENDPOINTS } from '../http/api.endpoints';
import { getWebsocketUrl } from '../http/realtime-url';
import { SecurityWarning, SensorDiagnostic } from '../models/sensor-diagnostic.model';
import { AuthService } from './auth.service';
import { RealtimeConnectionStatus } from './smoke-alert-realtime.service';

@Injectable({ providedIn: 'root' })
export class SensorDiagnosticRealtimeService {
  private readonly authService = inject(AuthService);

  private readonly diagnosticSubject = new Subject<SensorDiagnostic>();
  private readonly warningSubject = new Subject<SecurityWarning>();
  private readonly connectionStatusSubject = new BehaviorSubject<RealtimeConnectionStatus>(
    'DISCONNECTED',
  );

  private readonly sessionIds = new Set<number>();
  private readonly subscriptions = new Map<number, StompSubscription>();
  private client: Client | null = null;
  private warningSubscription: StompSubscription | null = null;

  readonly diagnostics$: Observable<SensorDiagnostic> = this.diagnosticSubject.asObservable();
  readonly warnings$: Observable<SecurityWarning> = this.warningSubject.asObservable();
  readonly connectionStatus$: Observable<RealtimeConnectionStatus> =
    this.connectionStatusSubject.asObservable();

  ensureConnected(): void {
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
      this.subscribeToWarnings(client);
      this.sessionIds.forEach((id) => this.subscribeToSession(client, id));
    };

    client.onStompError = (frame) => {
      console.error('Error STOMP de diagnósticos:', frame.headers, frame.body);
      this.connectionStatusSubject.next('ERROR');
    };

    client.onWebSocketError = (error) => {
      console.error('Error WebSocket de diagnósticos:', error);
      this.connectionStatusSubject.next('ERROR');
    };

    client.onWebSocketClose = () => {
      this.warningSubscription = null;
      this.subscriptions.clear();
      this.connectionStatusSubject.next(client.active ? 'RECONNECTING' : 'DISCONNECTED');
    };

    this.client = client;
    client.activate();
  }

  watchSession(id: number): void {
    this.sessionIds.add(id);
    this.ensureConnected();

    if (this.client?.connected) {
      this.subscribeToSession(this.client, id);
    }
  }

  unwatchSession(id: number): void {
    this.sessionIds.delete(id);
    this.subscriptions.get(id)?.unsubscribe();
    this.subscriptions.delete(id);
  }

  async disconnect(): Promise<void> {
    this.sessionIds.clear();
    this.subscriptions.forEach((subscription) => subscription.unsubscribe());
    this.subscriptions.clear();
    this.warningSubscription?.unsubscribe();
    this.warningSubscription = null;

    const client = this.client;
    this.client = null;

    if (client?.active) {
      await client.deactivate();
    }

    this.connectionStatusSubject.next('DISCONNECTED');
  }

  private subscribeToWarnings(client: Client): void {
    this.warningSubscription?.unsubscribe();

    this.warningSubscription = client.subscribe(
      API_ENDPOINTS.realtime.securityWarnings,
      (message) => this.processWarning(message),
    );
  }

  private subscribeToSession(client: Client, id: number): void {
    if (!client.connected || this.subscriptions.has(id)) {
      return;
    }

    const subscription = client.subscribe(API_ENDPOINTS.realtime.sensorDiagnostic(id), (message) =>
      this.processDiagnostic(message, id),
    );

    this.subscriptions.set(id, subscription);
  }

  private processDiagnostic(message: IMessage, expectedId: number): void {
    try {
      const diagnostic = JSON.parse(message.body) as SensorDiagnostic;

      if (diagnostic.id !== expectedId || !this.sessionIds.has(expectedId)) {
        return;
      }

      this.diagnosticSubject.next(diagnostic);

      if (diagnostic.status !== 'RUNNING') {
        this.unwatchSession(expectedId);
      }
    } catch (error) {
      console.error('El diagnóstico recibido no contiene un JSON válido.', error);
    }
  }

  private processWarning(message: IMessage): void {
    try {
      this.warningSubject.next(JSON.parse(message.body) as SecurityWarning);
    } catch (error) {
      console.error('La advertencia recibida no contiene un JSON válido.', error);
    }
  }
}
