import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SwPush } from '@angular/service-worker';
import { BehaviorSubject } from 'rxjs';

import { WebPushConfig } from '../models/push-notification.model';
import { PushNotificationService } from './push-notification.service';

const config: WebPushConfig = {
  supported: true,
  enabled: true,
  publicKey: 'clave-publica-prueba',
  subscribed: false,
  subscriptionCount: 0,
  message: 'Las notificaciones móviles están disponibles.',
};

class SwPushMock {
  readonly isEnabled = true;
  readonly subscriptionState = new BehaviorSubject<PushSubscription | null>(null);
  readonly subscription = this.subscriptionState.asObservable();
  readonly requestSubscription =
    vi.fn<(options: { serverPublicKey: string }) => Promise<PushSubscription>>();
}

describe('PushNotificationService', () => {
  let service: PushNotificationService;
  let http: HttpTestingController;
  let swPush: SwPushMock;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        PushNotificationService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SwPush, useClass: SwPushMock },
      ],
    });

    service = TestBed.inject(PushNotificationService);
    http = TestBed.inject(HttpTestingController);
    swPush = TestBed.inject(SwPush) as unknown as SwPushMock;
  });

  afterEach(() => http.verify());

  it('consulta la configuración y detecta que el dispositivo aún no está suscrito', async () => {
    const initialization = service.initialize();

    const request = http.expectOne((item) => item.url.endsWith('/notifications/push/config'));
    expect(request.request.method).toBe('GET');
    request.flush(config);

    await initialization;

    expect(service.serverConfigured()).toBe(true);
    expect(service.subscribed()).toBe(false);
    expect(service.subscriptionCount()).toBe(0);
  });

  it('registra la suscripción del navegador y actualiza el contador', async () => {
    const subscription = {
      endpoint: 'https://push.example/subscription-1',
      toJSON: () => ({
        endpoint: 'https://push.example/subscription-1',
        keys: { p256dh: 'p256dh-prueba', auth: 'auth-prueba' },
      }),
    } as unknown as PushSubscription;

    swPush.requestSubscription.mockResolvedValue(subscription);

    const initialization = service.initialize();
    const initialConfigRequest = http.expectOne((item) =>
      item.url.endsWith('/notifications/push/config'),
    );
    initialConfigRequest.flush(config);
    await initialization;

    const activation = service.enable();
    await new Promise((resolve) => setTimeout(resolve, 0));

    const registerRequest = http.expectOne((item) =>
      item.url.endsWith('/notifications/push/subscriptions'),
    );
    expect(registerRequest.request.method).toBe('POST');
    expect(registerRequest.request.body).toEqual(
      expect.objectContaining({
        endpoint: subscription.endpoint,
        keys: { p256dh: 'p256dh-prueba', auth: 'auth-prueba' },
      }),
    );
    registerRequest.flush({
      subscribed: true,
      message: 'Este dispositivo recibirá avisos.',
      timestamp: '2026-08-10T18:00:00Z',
    });

    await Promise.resolve();

    const updatedConfigRequest = http.expectOne((item) =>
      item.url.endsWith('/notifications/push/config'),
    );
    updatedConfigRequest.flush({ ...config, subscribed: true, subscriptionCount: 1 });

    await activation;

    expect(swPush.requestSubscription).toHaveBeenCalledWith({
      serverPublicKey: config.publicKey,
    });
    expect(service.subscribed()).toBe(true);
    expect(service.subscriptionCount()).toBe(1);
  });
});
