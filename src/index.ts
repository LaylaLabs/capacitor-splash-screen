import { registerPlugin } from '@capacitor/core';

import type { SplashScreenPlugin } from './definitions';

const LaylaSplashScreen = registerPlugin<SplashScreenPlugin>('LaylaSplashScreen', {
  web: () => import('./web').then((m) => new m.LaylaSplashScreenWeb()),
});

export * from './definitions';
export { LaylaSplashScreen };
export default LaylaSplashScreen;
