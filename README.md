# @laylalabs/capacitor-lottie-splash

Native 2-phase Lottie splash screen plugin for Capacitor with seamless frame-range looping and zero white-flash.

## Features

- **2-Phase Animations:** Play an obligatory intro animation (e.g. frames 0-130) followed by a seamless loop (e.g. frames 130-310) while the web app boots.
- **Zero White-Flash:** Direct native GPU view attached before the WebView renders, preventing white flashes on dark mode.
- **Declarative Configuration:** Configure animation paths, background colors, loop modes, and frames directly in `capacitor.config.ts`.
- **Flexible Loop Modes:** Choose between `none`, `full`, or custom `range`.
- **Framework Agnostic:** Compatible with Nuxt, Next, React, Vue, Svelte, or vanilla Capacitor.

## Install (GitHub Packages)

Configure your project `.npmrc` to resolve the `@laylalabs` scope from GitHub Packages:

```ini
@laylalabs:registry=https://npm.pkg.github.com
//npm.pkg.github.com/:_authToken=${GITHUB_TOKEN}
```

Then install via `pnpm`:

```bash
pnpm add @laylalabs/capacitor-lottie-splash
npx cap sync
```

## Configuration

In your `capacitor.config.ts`:

```typescript
import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  plugins: {
    LaylaLottieSplash: {
      animationPath: 'splash.json',
      backgroundColor: '#E55934',
      introFrames: [0, 130],
      loopMode: 'range',
      loopFrames: [130, 310],
      speed: 1.0,
      sizeDp: 260,
      fadeDurationMs: 400,
      autoDismiss: false,
      timeoutMs: 12000,
      immersive: true,
      staticImage: {
        imagePath: 'splash_static.png',
        scaleType: 'fitCenter',
      },
    },
  },
};

export default config;
```

## API

<docgen-index>

* [`notifyAppReady()`](#notifyappready)
* [`hide()`](#hide)
* [`show()`](#show)
* [`getConfig()`](#getconfig)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### notifyAppReady()

```typescript
notifyAppReady() => any
```

Signals that the application (Nuxt/Vue/React) has mounted and finished its initial render.
If the intro phase is still playing, the splash waits for the intro to finish,
then dismisses immediately with a smooth fade-out.

**Returns:** <code>any</code>

--------------------


### hide()

```typescript
hide() => any
```

Dismisses the splash screen immediately with a smooth fade-out,
bypassing any active intro or loop animations.

**Returns:** <code>any</code>

--------------------


### show()

```typescript
show() => any
```

Displays the splash screen overlay and starts playing the animation.

**Returns:** <code>any</code>

--------------------


### getConfig()

```typescript
getConfig() => any
```

Returns the active configuration parsed from capacitor.config.ts.

**Returns:** <code>any</code>

--------------------


### Interfaces


#### LottieSplashConfig

| Prop                  | Type                                                            | Description                                                                                                                                                                                                                                                                                     | Default                                                    |
| --------------------- | --------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------- |
| **`animationPath`**   | <code>string</code>                                             | File name or path of the Lottie animation JSON file. On Android: relative to assets/ (e.g., "ninheco_splash.json") On iOS: file name in the main app bundle (e.g., "ninheco_splash.json")                                                                                                       | <code>"splash.json"</code>                                 |
| **`backgroundColor`** | <code>string</code>                                             | Background color of the splash overlay in Hex (#RRGGBB or #AARRGGBB).                                                                                                                                                                                                                           | <code>"#FFFFFF"</code>                                     |
| **`introFrames`**     | <code>[number, number]</code>                                   | Intro animation frames [startFrame, endFrame]. The animation will play once from startFrame to endFrame before transitioning to the loop phase or waiting for app ready.                                                                                                                        |                                                            |
| **`loopMode`**        | <code><a href="#lottieloopmode">LottieLoopMode</a></code>       | Mode for animation looping while waiting for the application to be ready: - 'none': plays intro (or full animation) once and pauses on the last frame. - 'full': loops the entire animation continuously from frame 0 to end. - 'range': loops continuously between the specified `loopFrames`. | <code>"none" (or "range" if loopFrames is provided)</code> |
| **`loopFrames`**      | <code>[number, number]</code>                                   | Frame range for continuous looping while waiting for app initialization [loopStartFrame, loopEndFrame].                                                                                                                                                                                         |                                                            |
| **`speed`**           | <code>number</code>                                             | Animation playback speed multiplier.                                                                                                                                                                                                                                                            | <code>1.0</code>                                           |
| **`sizeDp`**          | <code>number</code>                                             | Dimension in Density-independent Pixels (DP) for the centered Lottie animation view.                                                                                                                                                                                                            | <code>260</code>                                           |
| **`fadeDurationMs`**  | <code>number</code>                                             | Duration of the smooth fade-out dismissal animation in milliseconds.                                                                                                                                                                                                                            | <code>400</code>                                           |
| **`autoDismiss`**     | <code>boolean</code>                                            | If true, dismisses the splash screen immediately when the animation finishes, without waiting for an explicit `notifyAppReady()` or `hide()` call.                                                                                                                                              | <code>false</code>                                         |
| **`timeoutMs`**       | <code>number</code>                                             | Safety watchdog timeout in milliseconds. If the app fails to call `notifyAppReady()`, the splash screen will dismiss automatically after this duration to prevent locking the user out.                                                                                                         | <code>12000</code>                                         |
| **`staticImage`**     | <code><a href="#staticimageconfig">StaticImageConfig</a></code> | Static image configuration displayed immediately before the Lottie animation begins.                                                                                                                                                                                                            |                                                            |
| **`immersive`**       | <code>boolean</code>                                            | Immersive mode: tints the system navigation bar (Home/Back/Recent buttons) and status bar with the splash background color, preventing black or white bars and ensuring total visual immersion. Restores system colors upon dismissal.                                                          | <code>true</code>                                          |


#### StaticImageConfig

| Prop                  | Type                                            | Description                                                                                                                   | Default                  |
| --------------------- | ----------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------- | ------------------------ |
| **`imagePath`**       | <code>string</code>                             | File name or path of the static splash image (in assets or drawable on Android, app bundle on iOS). e.g., "splash_static.png" |                          |
| **`scaleType`**       | <code><a href="#scaletype">ScaleType</a></code> | Image scale type.                                                                                                             | <code>"fitCenter"</code> |
| **`backgroundColor`** | <code>string</code>                             | Optional custom background color for the static image view container.                                                         |                          |


### Type Aliases


#### LottieLoopMode

<code>'none' | 'full' | 'range'</code>


#### ScaleType

<code>'fitCenter' | 'centerCrop' | 'centerInside'</code>

</docgen-api>
