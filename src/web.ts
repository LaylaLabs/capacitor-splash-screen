import { WebPlugin } from '@capacitor/core';

import type { SplashScreenConfig, SplashScreenPlugin } from './definitions';

export class LaylaSplashScreenWeb extends WebPlugin implements SplashScreenPlugin {
  private config: SplashScreenConfig = {
    backgroundColor: '#FFFFFF',
    fadeDurationMs: 400,
    autoDismiss: false,
    timeoutMs: 12000,
    immersive: true,
  };

  async notifyAppReady(): Promise<void> {
    if (typeof window !== 'undefined') {
      console.debug('[LaylaSplashScreenWeb] notifyAppReady received');
    }
  }

  async hide(): Promise<void> {
    if (typeof window !== 'undefined') {
      console.debug('[LaylaSplashScreenWeb] hide received');
    }
  }

  async show(): Promise<void> {
    if (typeof window !== 'undefined') {
      console.debug('[LaylaSplashScreenWeb] show received');
    }
  }

  async getConfig(): Promise<SplashScreenConfig> {
    return this.config;
  }
}
