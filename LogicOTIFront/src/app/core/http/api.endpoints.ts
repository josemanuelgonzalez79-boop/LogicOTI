export const API_ENDPOINTS = {
  auth: {
    login: '/auth/login',
  },

  users: {
    list: '/users',
    create: '/users',
    detail: (userId: number) => `/users/${userId}`,
  },

  building: {
    get: '/building',
    deviceSummary: '/building/device-summary',
  },

  areas: {
    state: (areaCode: string) => `/areas/${areaCode}/state`,
  },

  devices: {
    command: (deviceCode: string) => `/devices/${deviceCode}/command`,
  },

  history: {
    commands: '/history/commands',
    events: '/history/events',
  },

  alarms: {
    active: '/alarms/active',
  },

  sensorDiagnostics: {
    list: '/sensor-diagnostics',
    due: '/sensor-diagnostics/due',
    detail: (id: number) => `/sensor-diagnostics/${id}`,
    cancel: (id: number) => `/sensor-diagnostics/${id}/cancel`,
  },

  security: {
    bypasses: '/security/bypasses',
    bypass: (sensorCode: string) => `/security/bypasses/${sensorCode}`,
    warnings: '/security/warnings',
  },

  cameras: {
    list: '/cameras',
    detail: (cameraCode: string) => `/cameras/${cameraCode}`,
  },

  realtime: {
    websocket: '/ws',
    status: '/realtime/status',
    smokeAlerts: '/topic/alerts/smoke',
    areaState: (areaCode: string) => `/topic/areas/${areaCode}/state`,
    sensorDiagnostic: (id: number) => `/topic/diagnostics/sensors/${id}`,
    securityWarnings: '/topic/security/warnings',
  },

  plc: {
    status: '/plc/status',
    test: '/plc/test',
    light: '/plc/test/light',
  },

  system: {
    status: '/system/status',
  },
} as const;
