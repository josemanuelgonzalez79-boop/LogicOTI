export const API_ENDPOINTS = {
  auth: {
    login: '/auth/login',
  },

  building: {
    get: '/building',
  },

  areas: {
    state: (areaCode: string) => `/areas/${areaCode}/state`,
  },

  devices: {
    command: (deviceCode: string) =>
      `/devices/${deviceCode}/command`,
  },

  realtime: {
    websocket: '/ws',
    status: '/realtime/status',
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