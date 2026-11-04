package com.laylalabs.capacitor.splashscreen;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import androidx.core.content.ContextCompat;
import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginConfig;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "LaylaSplashScreen")
public class SplashScreenPlugin extends Plugin {
    private static final String TAG = "LaylaSplashScreen";

    /**
     * Sinalizador volátil público que indica quando a Splash Screen está pronta para exibição.
     * Permite que a SplashScreen nativa do Android 12+ (androidx.core.splashscreen) mantenha
     * a imagem estática canônica desde o milissegundo zero até a transição fluida para o Lottie.
     */
    public static volatile boolean isReady = false;
    public static volatile boolean isLottieReady = false; // Alias para retrocompatibilidade

    public static void setReady(boolean ready) {
        isReady = ready;
        isLottieReady = ready;
    }

    private FrameLayout splashOverlay;
    private LottieAnimationView lottieView;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean isAppReady = false;
    private boolean hasCompletedIntro = false;
    private boolean isDismissing = false;

    // Configurações globais
    private String backgroundColorHex = "auto";
    private String iconPath = null;
    private long fadeDurationMs = 400L;
    private boolean autoDismiss = false;
    private long timeoutMs = 12000L;
    private boolean immersive = true;

    // Configurações de animação (opcional)
    private boolean hasAnimation = false;
    private String animationPath = "splash.json";
    private float speed = 1.0f;
    private int sizeDp = 260;
    private int introStartFrame = -1;
    private int introEndFrame = -1;
    private String loopMode = "none";
    private int loopStartFrame = -1;
    private int loopEndFrame = -1;

    // Preservação de estado do sistema para restauração pós-dismissal
    private Integer originalStatusBarColor = null;
    private Integer originalNavigationBarColor = null;
    private Integer originalSystemUiVisibility = null;

    @Override
    public void load() {
        super.load();
        setReady(false);
        parseConfiguration();

        if (getActivity() != null) {
            getActivity().runOnUiThread(this::setupSplash);
            // Watchdog de segurança
            if (timeoutMs > 0) {
                handler.postDelayed(this::dismissSplashWithFade, timeoutMs);
            }
        }
    }

    protected void parseConfiguration() {
        PluginConfig config = getConfig();
        backgroundColorHex = config.getString("backgroundColor", "auto");
        iconPath = config.getString("iconPath", null);
        fadeDurationMs = (long) config.getInt("fadeDurationMs", 400);
        autoDismiss = config.getBoolean("autoDismiss", false);
        timeoutMs = (long) config.getInt("timeoutMs", 12000);
        immersive = config.getBoolean("immersive", true);

        // 1. Suporte ao subdicionário estruturado "animation"
        org.json.JSONObject animObj = config.getObject("animation");
        if (animObj != null) {
            hasAnimation = true;
            animationPath = animObj.optString("path", config.getString("animationPath", "splash.json"));
            speed = (float) animObj.optDouble("speed", config.getDouble("speed", 1.0));
            sizeDp = animObj.optInt("sizeDp", config.getInt("sizeDp", 260));
            loopMode = animObj.optString("loopMode", config.getString("loopMode", "none"));

            try {
                org.json.JSONArray introArr = animObj.optJSONArray("introFrames");
                if (introArr != null && introArr.length() >= 2) {
                    introStartFrame = introArr.getInt(0);
                    introEndFrame = introArr.getInt(1);
                }
            } catch (Throwable ignored) {
            }

            try {
                org.json.JSONArray loopArr = animObj.optJSONArray("loopFrames");
                if (loopArr != null && loopArr.length() >= 2) {
                    loopStartFrame = loopArr.getInt(0);
                    loopEndFrame = loopArr.getInt(1);
                    if ("none".equals(loopMode)) {
                        loopMode = "range";
                    }
                }
            } catch (Throwable ignored) {
            }
        } else if (config.getString("animationPath") != null) {
            // 2. Retrocompatibilidade: campos na raiz
            hasAnimation = true;
            animationPath = config.getString("animationPath", "splash.json");
            try {
                String speedStr = config.getString("speed", "1.0");
                speed = Float.parseFloat(speedStr);
            } catch (Throwable ignored) {
                speed = 1.0f;
            }
            sizeDp = config.getInt("sizeDp", 260);
            loopMode = config.getString("loopMode", "none");

            try {
                String[] introArr = config.getArray("introFrames");
                if (introArr != null && introArr.length >= 2) {
                    introStartFrame = Integer.parseInt(introArr[0].trim());
                    introEndFrame = Integer.parseInt(introArr[1].trim());
                }
            } catch (Throwable ignored) {
            }

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
        } else {
            hasAnimation = false;
        }
    }

    private void setupSplash() {
        if (getActivity() == null || splashOverlay != null) return;

        try {
            int parsedBgColor = Color.WHITE;
            if (backgroundColorHex != null && !backgroundColorHex.trim().isEmpty() && !"auto".equalsIgnoreCase(backgroundColorHex)) {
                try {
                    parsedBgColor = Color.parseColor(backgroundColorHex);
                } catch (Throwable e) {
                    parsedBgColor = Color.WHITE;
                }
            } else {
                // Fallback dinâmico: herda exatamente a cor nativa de inicialização da janela
                try {
                    int resId = getActivity().getResources().getIdentifier("splash_background", "color", getActivity().getPackageName());
                    if (resId != 0) {
                        parsedBgColor = ContextCompat.getColor(getActivity(), resId);
                    } else {
                        TypedValue typedValue = new TypedValue();
                        if (getActivity().getTheme().resolveAttribute(android.R.attr.windowSplashScreenBackground, typedValue, true)) {
                            parsedBgColor = typedValue.data;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }

            // Aplica modo imersivo (navigation bar + status bar na mesma cor de fundo)
            applyImmersiveMode(parsedBgColor);

            splashOverlay = new FrameLayout(getActivity());
            splashOverlay.setBackgroundColor(parsedBgColor);
            splashOverlay.setClickable(true);
            splashOverlay.setFocusable(true);

            if (hasAnimation && animationPath != null && !animationPath.trim().isEmpty()) {
                // Modo Lottie Animado
                lottieView = new LottieAnimationView(getActivity());
                lottieView.setAnimation(animationPath);
                lottieView.setSpeed(speed);

                lottieView.addLottieOnCompositionLoadedListener(composition -> {
                    setReady(true);
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
            } else {
                // Modo Estático (sem animação Lottie)
                hasCompletedIntro = true;
                setReady(true);
            }

            getActivity().addContentView(splashOverlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                splashOverlay.setForceDarkAllowed(false);
                if (lottieView != null) {
                    lottieView.setForceDarkAllowed(false);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Falha ao inicializar LaylaSplashScreen: " + t.getMessage(), t);
            setReady(true);
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
                setReady(false);
                setupSplash();
            });
        }
        call.resolve();
    }

    @PluginMethod
    public void getConfig(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("backgroundColor", backgroundColorHex);
        ret.put("iconPath", iconPath);
        ret.put("fadeDurationMs", fadeDurationMs);
        ret.put("autoDismiss", autoDismiss);
        ret.put("timeoutMs", timeoutMs);
        ret.put("immersive", immersive);

        if (hasAnimation) {
            JSObject anim = new JSObject();
            anim.put("path", animationPath);
            anim.put("speed", speed);
            anim.put("sizeDp", sizeDp);
            anim.put("loopMode", loopMode);
            ret.put("animation", anim);
        }

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
