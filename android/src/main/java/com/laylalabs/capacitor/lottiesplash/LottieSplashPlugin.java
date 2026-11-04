package com.laylalabs.capacitor.lottiesplash;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginConfig;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.InputStream;

@CapacitorPlugin(name = "LaylaLottieSplash")
public class LottieSplashPlugin extends Plugin {
    private static final String TAG = "LottieSplashPlugin";

    /**
     * Sinalizador volátil público que indica quando a animação Lottie está carregada e pronta para exibição.
     * Permite que a SplashScreen nativa do Android 12+ (androidx.core.splashscreen) mantenha
     * a imagem estática canônica desde o milissegundo zero até a transição fluida para o Lottie.
     */
    public static volatile boolean isLottieReady = false;

    private FrameLayout splashOverlay;
    private LottieAnimationView lottieView;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean isAppReady = false;
    private boolean hasCompletedIntro = false;
    private boolean isDismissing = false;

    // Configurações
    private String animationPath = "splash.json";
    private String backgroundColorHex = "#FFFFFF";
    private int introStartFrame = -1;
    private int introEndFrame = -1;
    private String loopMode = "none";
    private int loopStartFrame = -1;
    private int loopEndFrame = -1;
    private float speed = 1.0f;
    private int sizeDp = 260;
    private long fadeDurationMs = 400L;
    private boolean autoDismiss = false;
    private long timeoutMs = 12000L;
    private boolean immersive = true;

    // Preservação de estado do sistema para restauração pós-dismissal
    private Integer originalStatusBarColor = null;
    private Integer originalNavigationBarColor = null;
    private Integer originalSystemUiVisibility = null;

    @Override
    public void load() {
        super.load();
        parseConfiguration();

        if (getActivity() != null) {
            getActivity().runOnUiThread(this::setupLottieSplash);
            // Watchdog de segurança
            if (timeoutMs > 0) {
                handler.postDelayed(this::dismissSplashWithFade, timeoutMs);
            }
        }
    }

    private void parseConfiguration() {
        PluginConfig config = getConfig();
        animationPath = config.getString("animationPath", "splash.json");
        backgroundColorHex = config.getString("backgroundColor", "#FFFFFF");

        try {
            String speedStr = config.getString("speed", "1.0");
            speed = Float.parseFloat(speedStr);
        } catch (Throwable ignored) {
            speed = 1.0f;
        }

        sizeDp = config.getInt("sizeDp", 260);
        fadeDurationMs = (long) config.getInt("fadeDurationMs", 400);
        autoDismiss = config.getBoolean("autoDismiss", false);
        timeoutMs = (long) config.getInt("timeoutMs", 12000);
        loopMode = config.getString("loopMode", "none");
        immersive = config.getBoolean("immersive", true);

        // Intro frames [start, end]
        try {
            String[] introArr = config.getArray("introFrames");
            if (introArr != null && introArr.length >= 2) {
                introStartFrame = Integer.parseInt(introArr[0].trim());
                introEndFrame = Integer.parseInt(introArr[1].trim());
            }
        } catch (Throwable ignored) {
        }

        // Loop frames [start, end]
        try {
            String[] loopArr = config.getArray("loopFrames");
            if (loopArr != null && loopArr.length >= 2) {
                loopStartFrame = Integer.parseInt(loopArr[0].trim());
                loopEndFrame = Integer.parseInt(loopArr[1].trim());
                if ("none".equals(loopMode)) {
                    loopMode = "range";
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void setupLottieSplash() {
        if (getActivity() == null || splashOverlay != null) return;

        try {
            int parsedBgColor;
            try {
                parsedBgColor = Color.parseColor(backgroundColorHex);
            } catch (Throwable e) {
                parsedBgColor = Color.WHITE;
            }

            // Aplica modo imersivo (navigation bar + status bar na mesma cor de fundo)
            applyImmersiveMode(parsedBgColor);

            splashOverlay = new FrameLayout(getActivity());
            splashOverlay.setBackgroundColor(parsedBgColor);
            splashOverlay.setClickable(true);
            splashOverlay.setFocusable(true);

            // View de animação Lottie
            lottieView = new LottieAnimationView(getActivity());
            lottieView.setAnimation(animationPath);
            lottieView.setSpeed(speed);

            // Sinaliza prontidão assim que a animação for carregada na memória
            lottieView.addLottieOnCompositionLoadedListener(composition -> {
                isLottieReady = true;
            });

            boolean hasIntro = (introStartFrame >= 0 && introEndFrame > introStartFrame);

            if (hasIntro) {
                lottieView.setMinAndMaxFrame(introStartFrame, introEndFrame);
                lottieView.setRepeatCount(0);

                lottieView.addAnimatorListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (isDismissing) return;

                        if (!hasCompletedIntro) {
                            hasCompletedIntro = true;

                            if (isAppReady || autoDismiss) {
                                dismissSplashWithFade();
                            } else {
                                if ("range".equals(loopMode) && loopStartFrame >= 0 && loopEndFrame > loopStartFrame) {
                                    lottieView.post(() -> {
                                        if (isDismissing || lottieView == null) return;
                                        lottieView.setMinAndMaxFrame(loopStartFrame, loopEndFrame);
                                        lottieView.setRepeatCount(LottieDrawable.INFINITE);
                                        lottieView.playAnimation();
                                    });
                                } else if ("full".equals(loopMode)) {
                                    lottieView.post(() -> {
                                        if (isDismissing || lottieView == null) return;
                                        lottieView.setMinAndMaxFrame(0, (int) lottieView.getMaxFrame());
                                        lottieView.setRepeatCount(LottieDrawable.INFINITE);
                                        lottieView.playAnimation();
                                    });
                                }
                            }
                        }
                    }
                });
            } else {
                hasCompletedIntro = true;
                if ("range".equals(loopMode) && loopStartFrame >= 0 && loopEndFrame > loopStartFrame) {
                    lottieView.setMinAndMaxFrame(loopStartFrame, loopEndFrame);
                    lottieView.setRepeatCount(LottieDrawable.INFINITE);
                } else if ("full".equals(loopMode)) {
                    lottieView.setRepeatCount(LottieDrawable.INFINITE);
                } else {
                    lottieView.setRepeatCount(0);
                    if (autoDismiss) {
                        lottieView.addAnimatorListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                dismissSplashWithFade();
                            }
                        });
                    }
                }
            }

            lottieView.playAnimation();

            float density = getActivity().getResources().getDisplayMetrics().density;
            int sizeInPx = (int) (sizeDp * density);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(sizeInPx, sizeInPx);
            params.gravity = Gravity.CENTER;
            splashOverlay.addView(lottieView, params);

            getActivity().addContentView(splashOverlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                splashOverlay.setForceDarkAllowed(false);
                lottieView.setForceDarkAllowed(false);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Falha ao inicializar LottieSplash nativo: " + t.getMessage(), t);
        }
    }

    @PluginMethod
    public void notifyAppReady(PluginCall call) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                isAppReady = true;
                if (hasCompletedIntro) {
                    dismissSplashWithFade();
                }
            });
        }
        call.resolve();
    }

    @PluginMethod
    public void hide(PluginCall call) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(this::dismissSplashWithFade);
        }
        call.resolve();
    }

    @PluginMethod
    public void show(PluginCall call) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                isDismissing = false;
                hasCompletedIntro = false;
                isAppReady = false;
                setupLottieSplash();
            });
        }
        call.resolve();
    }

    @PluginMethod
    public void getConfig(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("animationPath", animationPath);
        ret.put("backgroundColor", backgroundColorHex);
        ret.put("loopMode", loopMode);
        ret.put("speed", speed);
        ret.put("sizeDp", sizeDp);
        ret.put("fadeDurationMs", fadeDurationMs);
        ret.put("autoDismiss", autoDismiss);
        ret.put("timeoutMs", timeoutMs);
        ret.put("immersive", immersive);
        call.resolve(ret);
    }

    private void dismissSplashWithFade() {
        if (isDismissing || splashOverlay == null) return;
        isDismissing = true;

        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (splashOverlay != null) {
                    splashOverlay.animate()
                        .alpha(0f)
                        .setDuration(fadeDurationMs)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (splashOverlay != null && splashOverlay.getParent() != null) {
                                    ((ViewGroup) splashOverlay.getParent()).removeView(splashOverlay);
                                    splashOverlay = null;
                                }
                                if (lottieView != null) {
                                    lottieView.cancelAnimation();
                                    lottieView = null;
                                }
                                restoreImmersiveMode();
                            }
                        })
                        .start();
                }
            });
        }
    }

    private void applyImmersiveMode(int parsedBgColor) {
        if (!immersive || getActivity() == null || getActivity().getWindow() == null) return;

        try {
            Window window = getActivity().getWindow();
            if (originalStatusBarColor == null) {
                originalStatusBarColor = window.getStatusBarColor();
            }
            if (originalNavigationBarColor == null) {
                originalNavigationBarColor = window.getNavigationBarColor();
            }
            if (originalSystemUiVisibility == null) {
                originalSystemUiVisibility = window.getDecorView().getSystemUiVisibility();
            }

            window.setStatusBarColor(parsedBgColor);
            window.setNavigationBarColor(parsedBgColor);

            boolean isDarkBg = isColorDark(parsedBgColor);
            int flags = window.getDecorView().getSystemUiVisibility();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (isDarkBg) {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                } else {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (isDarkBg) {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                } else {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                }
            }
            window.getDecorView().setSystemUiVisibility(flags);
        } catch (Throwable t) {
            Log.w(TAG, "Falha ao aplicar modo imersivo: " + t.getMessage());
        }
    }

    private void restoreImmersiveMode() {
        if (!immersive || getActivity() == null || getActivity().getWindow() == null) return;

        try {
            Window window = getActivity().getWindow();
            if (originalStatusBarColor != null) {
                window.setStatusBarColor(originalStatusBarColor);
            }
            if (originalNavigationBarColor != null) {
                window.setNavigationBarColor(originalNavigationBarColor);
            }
            if (originalSystemUiVisibility != null) {
                window.getDecorView().setSystemUiVisibility(originalSystemUiVisibility);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Falha ao restaurar modo imersivo: " + t.getMessage());
        }
    }

    private boolean isColorDark(int color) {
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return darkness >= 0.5;
    }
}
