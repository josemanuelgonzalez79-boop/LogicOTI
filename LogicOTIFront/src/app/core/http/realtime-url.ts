import { environment } from '../../../environments/environment';

export function getWebsocketUrl(): string {
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
  const authority = window.location.host || 'localhost';

  return `${protocol}://${authority}${environment.websocketPath}`;
}
