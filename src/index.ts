import { registerPlugin } from '@capacitor/core';

import type { LottieSplashPlugin } from './definitions';

const LaylaLottieSplash = registerPlugin<LottieSplashPlugin>('LaylaLottieSplash', {
  web: () => import('./web').then((m) => new m.LaylaLottieSplashWeb()),
});

export * from './definitions';
export { LaylaLottieSplash };
export default LaylaLottieSplash;
