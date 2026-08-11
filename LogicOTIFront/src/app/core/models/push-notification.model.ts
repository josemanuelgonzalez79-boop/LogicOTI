export interface WebPushConfig {
  supported: boolean;
  enabled: boolean;
  publicKey: string;
  subscribed: boolean;
  subscriptionCount: number;
  message: string;
}

export interface WebPushSubscriptionRequest {
  endpoint: string;
  keys: {
    p256dh: string;
    auth: string;
  };
  userAgent: string;
}

export interface WebPushUnsubscribeRequest {
  endpoint: string;
}

export interface WebPushSubscriptionResponse {
  subscribed: boolean;
  message: string;
  timestamp: string;
}
