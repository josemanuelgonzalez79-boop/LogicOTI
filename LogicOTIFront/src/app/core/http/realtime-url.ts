import { environment } from '../../../environments/environment';

export function getWebsocketUrl(): string {
  const configuredPath = environment.websocketPath.trim();

  if (/^wss?:\/\//i.test(configuredPath)) {
    return configuredPath;
  }

  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
  const authority = window.location.host || 'localhost';
  const path = configuredPath.startsWith('/')
    ? configuredPath
    : `/${configuredPath}`;

  return `${protocol}://${authority}${path}`;
}