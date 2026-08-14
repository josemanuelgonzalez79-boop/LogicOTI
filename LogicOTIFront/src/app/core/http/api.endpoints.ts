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
    executiveMonthlyReport: '/reports/executive/monthly',
    retention: '/history/retention',
    runRetention: '/history/retention/run',
  },

  alarms: {
    active: '/alarms/active',
    activity: (eventId: number) => `/alarms/${eventId}/activity`,
    acknowledgement: (eventId: number) => `/alarms/${eventId}/acknowledgement`,
    comments: (eventId: number) => `/alarms/${eventId}/comments`,
  },

  notifications: {
    pushConfig: '/notifications/push/config',
    pushSubscriptions: '/notifications/push/subscriptions',
    pushTest: '/notifications/push/test',
  },

  sensorDiagnostics: {
    list: '/sensor-diagnostics',
    due: '/sensor-diagnostics/due',
    detail: (id: number) => `/sensor-diagnostics/${id}`,
    cancel: (id: number) => `/sensor-diagnostics/${id}/cancel`,
  },

  security: {
    status: '/security/status',
    precheck: '/security/precheck',
    arm: '/security/arm',
    disarm: '/security/disarm',
    schedules: '/security/schedules',
    automaticLightingStatus: '/security/automatic-lighting/status',
    areaInactivityStatus: '/security/inactivity/status',
    bypasses: '/security/bypasses',
    bypass: (sensorCode: string) => `/security/bypasses/${sensorCode}`,
    warnings: '/security/warnings',
  },

  cameras: {
    list: '/cameras',
    detail: (cameraCode: string) => `/cameras/${cameraCode}`,
    alertView: '/camera-alerts/view',
  },

  realtime: {
    websocket: '/ws',
    status: '/realtime/status',
    smokeAlerts: '/topic/alerts/smoke',
    alarmAttention: '/topic/alerts/attention',
    areaState: (areaCode: string) => `/topic/areas/${areaCode}/state`,
    sensorDiagnostic: (id: number) => `/topic/diagnostics/sensors/${id}`,
    securityStatus: '/topic/security/status',
    securityWarnings: '/topic/security/warnings',
    securityMotionAlerts: '/topic/security/motion-alerts',
    automaticLighting: '/topic/security/automatic-lighting',
    areaInactivity: '/topic/security/inactivity',
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
