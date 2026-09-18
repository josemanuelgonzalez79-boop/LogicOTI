import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { SwPush } from '@angular/service-worker';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../../environments/environment';
import { API_ENDPOINTS } from '../http/api.endpoints';
import {
  WebPushConfig,
  WebPushSubscriptionRequest,
  WebPushSubscriptionResponse,
  WebPushTestResponse,
  WebPushUnsubscribeRequest,
} from '../models/push-notification.model';

@Injectable({ providedIn: 'root' })
export class PushNotificationService {
  private readonly http = inject(HttpClient);
  private readonly swPush = inject(SwPush);
  private readonly baseUrl = environment.apiBaseUrl;

  readonly loading = signal(false);
  readonly browserSupported = signal(this.swPush.isEnabled);
  readonly serverConfigured = signal(false);
  readonly subscribed = signal(false);
  readonly subscriptionCount = signal(0);
  readonly message = signal('Consultando las notificaciones de este dispositivo...');

  private config: WebPushConfig | null = null;

  async initialize(): Promise<void> {
    this.loading.set(true);

    try {
      this.config = await firstValueFrom(
        this.http.get<WebPushConfig>(`${this.baseUrl}${API_ENDPOINTS.notifications.pushConfig}`),
      );

      this.serverConfigured.set(this.config.enabled);
      this.subscriptionCount.set(this.config.subscriptionCount);

      if (!this.swPush.isEnabled) {
        this.browserSupported.set(false);
        this.subscribed.set(false);
        this.message.set(
          environment.production
            ? 'Este navegador no permite notificaciones Web Push.'
            : 'Las notificaciones se habilitan en la versión instalada con HTTPS.',
        );
        return;
      }

      const subscription = await firstValueFrom(this.swPush.subscription);
      this.subscribed.set(Boolean(subscription));

      if (subscription && this.config.enabled) {
        await this.registerSubscription(subscription, false);
        return;
      }

      this.message.set(
        this.config.enabled
          ? 'Activa los avisos para recibir alarmas aunque la pantalla esté cerrada.'
          : this.config.message,
      );
    } catch (error) {
      this.message.set(this.errorMessage(error, 'No fue posible consultar las notificaciones.'));
    } finally {
      this.loading.set(false);
    }
  }

  async enable(): Promise<void> {
    if (this.loading()) {
      return;
    }

    this.loading.set(true);

    try {
      if (!this.swPush.isEnabled) {
        throw new Error(
          environment.production
            ? 'Este navegador no permite notificaciones Web Push.'
            : 'Prueba esta función desde la versión de producción publicada con HTTPS.',
        );
      }

      if (!this.config) {
        this.config = await firstValueFrom(
          this.http.get<WebPushConfig>(`${this.baseUrl}${API_ENDPOINTS.notifications.pushConfig}`),
        );
      }

      if (!this.config.enabled || !this.config.publicKey) {
        throw new Error(this.config.message);
      }

      const subscription = await this.swPush.requestSubscription({
        serverPublicKey: this.config.publicKey,
      });

      await this.registerSubscription(subscription, true);
    } catch (error) {
      this.message.set(
        this.errorMessage(
          error,
          'No fue posible activar los avisos. Revisa el permiso de notificaciones del navegador.',
        ),
      );
    } finally {
      this.loading.set(false);
    }
  }

  async disable(): Promise<void> {
    if (this.loading() || !this.swPush.isEnabled) {
      return;
    }

    this.loading.set(true);

    try {
      const subscription = await firstValueFrom(this.swPush.subscription);

      if (!subscription) {
        this.subscribed.set(false);
        this.message.set('Los avisos ya estaban desactivados en este dispositivo.');
        return;
      }

      const request: WebPushUnsubscribeRequest = {
        endpoint: subscription.endpoint,
      };

      const response = await firstValueFrom(
        this.http.delete<WebPushSubscriptionResponse>(
          `${this.baseUrl}${API_ENDPOINTS.notifications.pushSubscriptions}`,
          { body: request },
        ),
      );

      await subscription.unsubscribe();
      this.subscribed.set(false);
      this.subscriptionCount.set(Math.max(0, this.subscriptionCount() - 1));
      this.message.set(response.message);
    } catch (error) {
      this.message.set(this.errorMessage(error, 'No fue posible desactivar los avisos.'));
    } finally {
      this.loading.set(false);
    }
  }

  async sendTest(): Promise<void> {
    if (this.loading() || !this.subscribed()) {
      return;
    }

    this.loading.set(true);

    try {
      const response = await firstValueFrom(
        this.http.post<WebPushTestResponse>(
          `${this.baseUrl}${API_ENDPOINTS.notifications.pushTest}`,
          {},
        ),
      );

      this.message.set(response.message);
    } catch (error) {
      this.message.set(
        this.errorMessage(error, 'No fue posible enviar la notificación de prueba.'),
      );
    } finally {
      this.loading.set(false);
    }
  }

  private async registerSubscription(
    subscription: PushSubscription,
    showActivationMessage: boolean,
  ): Promise<void> {
    const json = subscription.toJSON();
    const p256dh = json.keys?.['p256dh'];
    const auth = json.keys?.['auth'];

    if (!json.endpoint || !p256dh || !auth) {
      throw new Error('El navegador no entregó una suscripción Web Push completa.');
    }

    const request: WebPushSubscriptionRequest = {
      endpoint: json.endpoint,
      keys: { p256dh, auth },
      userAgent: navigator.userAgent,
    };

    const response = await firstValueFrom(
      this.http.post<WebPushSubscriptionResponse>(
        `${this.baseUrl}${API_ENDPOINTS.notifications.pushSubscriptions}`,
        request,
      ),
    );

    const updatedConfig = await firstValueFrom(
      this.http.get<WebPushConfig>(`${this.baseUrl}${API_ENDPOINTS.notifications.pushConfig}`),
    );

    this.config = updatedConfig;
    this.serverConfigured.set(updatedConfig.enabled);

    this.subscribed.set(true);
    this.subscriptionCount.set(updatedConfig.subscriptionCount);
    this.message.set(
      showActivationMessage ? response.message : 'Los avisos están activos en este dispositivo.',
    );
  }

  private errorMessage(error: unknown, fallback: string): string {
    if (error instanceof HttpErrorResponse) {
      const detail = error.error?.detail ?? error.error?.message;
      return typeof detail === 'string' && detail.trim() ? detail : fallback;
    }

    if (error instanceof Error && error.message.trim()) {
      return error.message;
    }

    return fallback;
  }
}
