import { registerPlugin } from '@capacitor/core';

import type { LottieSplashPlugin } from './definitions';

const LaylaLottieSplash = registerPlugin<LottieSplashPlugin>('LaylaLottieSplash', {
  web: () => import('./web').then((m) => new m.LottieSplashWeb()),
});

export * from './definitions';
export { LaylaLottieSplash, LaylaLottieSplash as LottieSplash };
export default LaylaLottieSplash;
