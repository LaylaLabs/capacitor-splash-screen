import { registerPlugin } from '@capacitor/core';

import type { SplashScreenPlugin } from './definitions';

const LaylaSplashScreen = registerPlugin<SplashScreenPlugin>('LaylaSplashScreen', {
  web: () => import('./web').then((m) => new m.LaylaSplashScreenWeb()),
});

// Alias for backwards compatibility
const LaylaLottieSplash = LaylaSplashScreen;

export * from './definitions';
export { LaylaSplashScreen, LaylaLottieSplash };
export default LaylaSplashScreen;
