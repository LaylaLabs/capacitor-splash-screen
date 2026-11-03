import { WebPlugin } from '@capacitor/core';

import type { LottieSplashConfig, LottieSplashPlugin } from './definitions';

export class LottieSplashWeb extends WebPlugin implements LottieSplashPlugin {
  private config: LottieSplashConfig = {
    animationPath: 'splash.json',
    backgroundColor: '#FFFFFF',
    loopMode: 'none',
    speed: 1.0,
    sizeDp: 260,
    fadeDurationMs: 400,
    autoDismiss: false,
    timeoutMs: 12000,
  };

  async notifyAppReady(): Promise<void> {
    if (typeof window !== 'undefined') {
      console.debug('[LottieSplashWeb] notifyAppReady received');
    }
  }

  async hide(): Promise<void> {
    if (typeof window !== 'undefined') {
      console.debug('[LottieSplashWeb] hide received');
    }
  }

  async show(): Promise<void> {
    if (typeof window !== 'undefined') {
      console.debug('[LottieSplashWeb] show received');
    }
  }

  async getConfig(): Promise<LottieSplashConfig> {
    return this.config;
  }
}
