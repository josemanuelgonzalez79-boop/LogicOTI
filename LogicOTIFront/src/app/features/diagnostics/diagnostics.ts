import {
  Component,
  OnDestroy,
  inject,
  signal,
} from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Client,
  IMessage,
  StompSubscription,
} from '@stomp/stompjs';

import { environment } from '../../../environments/environment';
import { AreaState } from '../../core/models/area-state.model';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-diagnostics',
  standalone: true,
  imports: [],
  templateUrl: './diagnostics.html',
  styleUrl: './diagnostics.scss',
})
export class Diagnostics implements OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);

  // Resultado de pruebas REST
  readonly loading = signal(false);
  readonly activeTest = signal('');
  readonly responseStatus = signal('');
  readonly responseBody = signal('');

  // Estado independiente del WebSocket
  readonly websocketConnected = signal(false);
  readonly websocketStatus = signal('Desconectado');
  readonly websocketError = signal('');
  readonly websocketLastMessage = signal('');
  readonly websocketMessages = signal<AreaState[]>([]);

  private stompClient: Client | null = null;
  private areaSubscription: StompSubscription | null = null;

  testSystemStatus(): void {
    this.executeRequest(
      'GET /api/system/status',
      this.http.get(
        `${environment.apiBaseUrl}/system/status`,
      ),
    );
  }

  testBuilding(): void {
    this.executeRequest(
      'GET /api/building',
      this.http.get(
        `${environment.apiBaseUrl}/building`,
      ),
    );
  }

  testAreaState(): void {
    this.executeRequest(
      'GET /api/areas/P1_A01/state',
      this.http.get<AreaState>(
        `${environment.apiBaseUrl}/areas/P1_A01/state`,
      ),
    );
  }

  turnOnDevice(): void {
    this.sendDeviceCommand(true);
  }

  turnOffDevice(): void {
    this.sendDeviceCommand(false);
  }

  testHistory(): void {
    this.executeRequest(
      'GET /api/history/commands',
      this.http.get(
        `${environment.apiBaseUrl}/history/commands`,
        {
          params: {
            areaCode: 'P1_A01',
            deviceCode: 'P1_A01_MS02',
            limit: 20,
            offset: 0,
          },
        },
      ),
    );
  }

  testRealtimeStatus(): void {
    this.executeRequest(
      'GET /api/realtime/status',
      this.http.get(
        `${environment.apiBaseUrl}/realtime/status`,
      ),
    );
  }

  testPlc(): void {
    this.executeRequest(
      'GET /api/plc/test',
      this.http.get(
        `${environment.apiBaseUrl}/plc/test`,
      ),
    );
  }

  turnOnPlcLight(): void {
    this.sendPlcLightCommand(true);
  }

  turnOffPlcLight(): void {
    this.sendPlcLightCommand(false);
  }

  private sendPlcLightCommand(on: boolean): void {
    const action = on ? 'Encender' : 'Apagar';

    this.executeRequest(
      `PUT /api/plc/test/light — ${action}`,
      this.http.put(
        `${environment.apiBaseUrl}/plc/test/light`,
        { on },
      ),
    );
  }

  connectWebSocket(): void {
    if (this.stompClient?.active) {
      this.websocketStatus.set(
        'La conexión ya está activa o intentando conectarse.',
      );
      return;
    }

    const token = this.authService.getToken();

    if (!token) {
      this.websocketConnected.set(false);
      this.websocketStatus.set('No autenticado');
      this.websocketError.set(
        'No existe un token JWT válido.',
      );
      return;
    }

    this.websocketConnected.set(false);
    this.websocketStatus.set('Conectando...');
    this.websocketError.set('');
    this.websocketLastMessage.set('');
    this.websocketMessages.set([]);

    this.stompClient = new Client({
      brokerURL: environment.websocketUrl,

      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },

      // Durante las pruebas no reconectamos automáticamente.
      // Así un error STOMP no se repite continuamente.
      reconnectDelay: 0,

      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,

      onConnect: () => {
        this.websocketConnected.set(true);
        this.websocketStatus.set(
          'Conectado y suscrito a P1_A01',
        );
        this.websocketError.set('');

        this.subscribeToArea('P1_A01');
      },

      onStompError: (frame) => {
        this.websocketConnected.set(false);
        this.websocketStatus.set('Error STOMP');

        this.websocketError.set(
          this.formatJson({
            headers: frame.headers,
            body: frame.body,
          }),
        );

        console.error('Error STOMP:', {
          headers: frame.headers,
          body: frame.body,
        });
      },

      onWebSocketError: (error) => {
        this.websocketConnected.set(false);
        this.websocketStatus.set(
          'Error en la conexión WebSocket',
        );

        this.websocketError.set(
          'No fue posible establecer la conexión WebSocket.',
        );

        console.error('Error WebSocket:', error);
      },

      onWebSocketClose: (event) => {
        this.websocketConnected.set(false);

        if (this.websocketStatus() !== 'Error STOMP') {
          this.websocketStatus.set('Desconectado');
        }

        console.log('WebSocket cerrado:', event);
      },

      onDisconnect: () => {
        this.websocketConnected.set(false);
        this.websocketStatus.set('Desconectado');
      },

      debug: (message) => {
        console.debug('[STOMP]', message);
      },
    });

    this.stompClient.activate();
  }

  async disconnectWebSocket(): Promise<void> {
    this.areaSubscription?.unsubscribe();
    this.areaSubscription = null;

    const client = this.stompClient;
    this.stompClient = null;

    if (client?.active) {
      await client.deactivate();
    }

    this.websocketConnected.set(false);
    this.websocketStatus.set('Desconectado');
  }

  clearResponse(): void {
    this.activeTest.set('');
    this.responseStatus.set('');
    this.responseBody.set('');
  }

  clearWebSocketMessages(): void {
    this.websocketMessages.set([]);
    this.websocketLastMessage.set('');
    this.websocketError.set('');
  }

  ngOnDestroy(): void {
    void this.disconnectWebSocket();
  }

  private sendDeviceCommand(on: boolean): void {
    const action = on ? 'Encender' : 'Apagar';

    this.executeRequest(
      `PUT /api/devices/P1_A01_MS02/command — ${action}`,
      this.http.put<AreaState>(
        `${environment.apiBaseUrl}/devices/P1_A01_MS02/command`,
        { on },
      ),
    );
  }

  private executeRequest(
    testName: string,
    request$: Observable<unknown>,
  ): void {
    this.loading.set(true);
    this.activeTest.set(testName);
    this.responseStatus.set('Procesando...');
    this.responseBody.set('');

    request$.subscribe({
      next: (response) => {
        this.loading.set(false);
        this.responseStatus.set('Solicitud exitosa');
        this.responseBody.set(this.formatJson(response));
      },

      error: (error) => {
        this.loading.set(false);

        this.responseStatus.set(
          `Error HTTP ${error.status || 'desconocido'}`,
        );

        this.responseBody.set(
          this.formatJson(
            error.error ?? {
              message: error.message,
              status: error.status,
            },
          ),
        );
      },
    });
  }

  private subscribeToArea(areaCode: string): void {
    if (!this.stompClient?.connected) {
      this.websocketError.set(
        'No se puede crear la suscripción porque STOMP no está conectado.',
      );
      return;
    }

    this.areaSubscription?.unsubscribe();

    this.areaSubscription = this.stompClient.subscribe(
      `/topic/areas/${areaCode}/state`,
      (message: IMessage) => {
        try {
          const state = JSON.parse(
            message.body,
          ) as AreaState;

          this.websocketMessages.update((messages) => [
            state,
            ...messages,
          ]);

          this.websocketLastMessage.set(
            this.formatJson(state),
          );

          this.websocketStatus.set(
            `Conectado — mensaje recibido de ${areaCode}`,
          );
        } catch (error) {
          this.websocketError.set(
            'El mensaje recibido no contiene un JSON válido.',
          );

          console.error(
            'No fue posible interpretar el mensaje STOMP:',
            error,
          );
        }
      },
    );
  }

  private formatJson(value: unknown): string {
    return JSON.stringify(value, null, 2);
  }
}
