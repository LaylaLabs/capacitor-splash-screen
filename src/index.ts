import { registerPlugin } from '@capacitor/core';

import type { LottieSplashPlugin } from './definitions';

const LottieSplash = registerPlugin<LottieSplashPlugin>('LottieSplash', {
  web: () => import('./web').then((m) => new m.LottieSplashWeb()),
});

export * from './definitions';
export { LottieSplash };
export default LottieSplash;
