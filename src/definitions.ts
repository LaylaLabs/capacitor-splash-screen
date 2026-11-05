export type LottieLoopMode = 'none' | 'full' | 'range';

export interface SplashAnimationConfig {
  /**
   * File name or path of the Lottie animation JSON file.
   * On Android: relative to assets/ (e.g., "ninheco_splash.json")
   * On iOS: file name in the main app bundle (e.g., "ninheco_splash.json")
   * @default "splash.json"
   */
  path?: string;

  /**
   * Animation playback speed multiplier.
   * @default 1.0
   */
  speed?: number;

  /**
   * Dimension in Density-independent Pixels (DP) for the centered Lottie animation view.
   * If omitted or <= 0, or if `fullscreen: true` is set, the animation view expands to MATCH_PARENT
   * with FIT_CENTER scaling, allowing expanding waves, particles, and full-screen effects.
   * @default 0 (fullscreen: true)
   */
  sizeDp?: number;

  /**
   * If true, the animation view expands to fill the entire screen (MATCH_PARENT)
   * with aspect fit scaling, preventing clipping of expanding elements, waves, or ripples.
   * @default true (when sizeDp is omitted or <= 0)
   */
  fullscreen?: boolean;

  /**
   * If true, loops the entire animation continuously from frame 0 to end while waiting
   * for the app to be ready. Shortcut for `loopMode: 'full'`.
   * @default true (when introFrames and loopFrames are not specified)
   */
  loop?: boolean;

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
   * Minimum number of full loop cycles to play before allowing dismissal when app is ready.
   * If set to 1, the animation is guaranteed to play the intro AND at least one full loop cycle
   * before fading out.
   * @default 0
   */
  minLoopCycles?: number;
}

export interface SplashScreenConfig {
  /**
   * Background color of the splash screen in Hex (#RRGGBB or #AARRGGBB).
   * Serves as the Single Source of Truth (SSOT) for both the 0ms static window
   * and the runtime overlay.
   * @default "#FFFFFF"
   */
  backgroundColor?: string;

  /**
   * Path or resource identifier for the static launch icon displayed at millisecond zero.
   * On Android: drawable/mipmap name (e.g., "splash_logo").
   * On iOS: Image set name in Assets catalog (e.g., "SplashLogo").
   */
  iconPath?: string;

  /**
   * Optional Lottie vector animation configuration.
   * If omitted, the splash screen acts as a pure native static splash with smooth fade-out.
   */
  animation?: SplashAnimationConfig;

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
   * Immersive mode: tints the system navigation bar (Home/Back/Recent buttons) and status bar
   * with the splash background color, preventing black or white bars and ensuring total visual immersion.
   * Restores system colors upon dismissal.
   * @default true
   */
  immersive?: boolean;

  // Backwards compatibility for root-level animation fields
  /** @deprecated Use animation.path instead */
  animationPath?: string;
  /** @deprecated Use animation.speed instead */
  speed?: number;
  /** @deprecated Use animation.sizeDp instead */
  sizeDp?: number;
  /** @deprecated Use animation.introFrames instead */
  introFrames?: [number, number];
  /** @deprecated Use animation.loopMode instead */
  loopMode?: LottieLoopMode;
  /** @deprecated Use animation.loopFrames instead */
  loopFrames?: [number, number];
}

export interface SplashScreenPlugin {
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
  getConfig(): Promise<SplashScreenConfig>;
}

// Backwards-compatibility aliases
export type LottieSplashConfig = SplashScreenConfig;
export type LottieSplashPlugin = SplashScreenPlugin;
