export const API_ENDPOINTS = {
  plc: {
    status: '/plc/status',
    test: '/plc/test',
    light: '/plc/test/light',
  },

  system: {
    status: '/system/status',
  },
} as const;