export type LottieLoopMode = 'none' | 'full' | 'range';

export type ScaleType = 'fitCenter' | 'centerCrop' | 'centerInside';

export interface StaticImageConfig {
  /**
   * File name or path of the static splash image (in assets or drawable on Android, app bundle on iOS).
   * e.g., "splash_static.png"
   */
  imagePath: string;

  /**
   * Image scale type.
   * @default "fitCenter"
   */
  scaleType?: ScaleType;

  /**
   * Optional custom background color for the static image view container.
   */
  backgroundColor?: string;
}

export interface LottieSplashConfig {
  /**
   * File name or path of the Lottie animation JSON file.
   * On Android: relative to assets/ (e.g., "ninheco_splash.json")
   * On iOS: file name in the main app bundle (e.g., "ninheco_splash.json")
   * @default "splash.json"
   */
  animationPath?: string;

  /**
   * Background color of the splash overlay in Hex (#RRGGBB or #AARRGGBB).
   * @default "#FFFFFF"
   */
  backgroundColor?: string;

  /**
   * Intro animation frames [startFrame, endFrame].
   * The animation will play once from startFrame to endFrame before transitioning
   * to the loop phase or waiting for app ready.
   * @example [0, 130]
   */
  introFrames?: [number, number];

  /**
   * Mode for animation looping while waiting for the application to be ready:
   * - 'none': plays intro (or full animation) once and pauses on the last frame.
   * - 'full': loops the entire animation continuously from frame 0 to end.
   * - 'range': loops continuously between the specified `loopFrames`.
   * @default "none" (or "range" if loopFrames is provided)
   */
  loopMode?: LottieLoopMode;

  /**
   * Frame range for continuous looping while waiting for app initialization [loopStartFrame, loopEndFrame].
   * @example [130, 310]
   */
  loopFrames?: [number, number];

  /**
   * Animation playback speed multiplier.
   * @default 1.0
   */
  speed?: number;

  /**
   * Dimension in Density-independent Pixels (DP) for the centered Lottie animation view.
   * @default 260
   */
  sizeDp?: number;

  /**
   * Duration of the smooth fade-out dismissal animation in milliseconds.
   * @default 400
   */
  fadeDurationMs?: number;

  /**
   * If true, dismisses the splash screen immediately when the animation finishes,
   * without waiting for an explicit `notifyAppReady()` or `hide()` call.
   * @default false
   */
  autoDismiss?: boolean;

  /**
   * Safety watchdog timeout in milliseconds. If the app fails to call `notifyAppReady()`,
   * the splash screen will dismiss automatically after this duration to prevent locking the user out.
   * @default 12000
   */
  timeoutMs?: number;

  /**
   * Static image configuration displayed immediately before the Lottie animation begins.
   */
  staticImage?: StaticImageConfig;

  /**
   * Immersive mode: tints the system navigation bar (Home/Back/Recent buttons) and status bar
   * with the splash background color, preventing black or white bars and ensuring total visual immersion.
   * Restores system colors upon dismissal.
   * @default true
   */
  immersive?: boolean;
}

export interface LottieSplashPlugin {
  /**
   * Signals that the application (Nuxt/Vue/React) has mounted and finished its initial render.
   * If the intro phase is still playing, the splash waits for the intro to finish,
   * then dismisses immediately with a smooth fade-out.
   */
  notifyAppReady(): Promise<void>;

  /**
   * Dismisses the splash screen immediately with a smooth fade-out,
   * bypassing any active intro or loop animations.
   */
  hide(): Promise<void>;

  /**
   * Displays the splash screen overlay and starts playing the animation.
   */
  show(): Promise<void>;

  /**
   * Returns the active configuration parsed from capacitor.config.ts.
   */
  getConfig(): Promise<LottieSplashConfig>;
}
