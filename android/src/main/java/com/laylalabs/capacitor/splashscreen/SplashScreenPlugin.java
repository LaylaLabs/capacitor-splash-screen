package com.laylalabs.capacitor.splashscreen;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.core.content.ContextCompat;
import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.LottieCompositionFactory;
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

    public static void setReady(boolean ready) {
        isReady = ready;
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
    private int sizeDp = 0;
    private boolean fullscreen = true;
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
        parseConfiguration();
        setReady(!hasAnimation);

        if (getActivity() != null) {
            setupSplash();

            // Watchdog para liberação garantida da splash nativa (máximo 2.5s se Lottie atrasar)
            handler.postDelayed(() -> setReady(true), 2500);

            // Watchdog de segurança geral
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
            
            float defaultSpeed = 1.0f;
            try {
                String speedStr = config.getString("speed", "1.0");
                defaultSpeed = Float.parseFloat(speedStr);
            } catch (Throwable ignored) {
            }
            speed = (float) animObj.optDouble("speed", (double) defaultSpeed);

            sizeDp = animObj.optInt("sizeDp", config.getInt("sizeDp", 0));
            fullscreen = animObj.optBoolean("fullscreen", config.getBoolean("fullscreen", sizeDp <= 0));

            loopMode = animObj.optString("loopMode", config.getString("loopMode", "none"));
            if (animObj.has("loop")) {
                boolean loopBool = animObj.optBoolean("loop", true);
                loopMode = loopBool ? "full" : "none";
            }

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
                    if ("none".equals(loopMode) && !animObj.has("loop")) {
                        loopMode = "range";
                    }
                }
            } catch (Throwable ignored) {
            }

            // Padrão inteligente: se não houver frames de corte e loop não foi desativado explicitamente, faz loop completo
            if ("none".equals(loopMode) && introStartFrame < 0 && loopStartFrame < 0 && !animObj.has("loop")) {
                loopMode = "full";
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

            // Adiciona overlay à Activity imediatamente
            getActivity().addContentView(splashOverlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                splashOverlay.setForceDarkAllowed(false);
            }

            if (hasAnimation && animationPath != null && !animationPath.trim().isEmpty()) {
                String assetName = animationPath.trim();
                if (assetName.startsWith("assets/")) {
                    assetName = assetName.substring(7);
                }
                if (!assetName.endsWith(".json")) {
                    assetName = assetName + ".json";
                }

                // Fallback resiliente: verifica se está na raiz de assets/ ou sob public/ (padrão web Capacitor)
                String resolvedAsset = assetName;
                try {
                    getActivity().getAssets().open(resolvedAsset).close();
                } catch (Throwable e) {
                    if (!resolvedAsset.startsWith("public/")) {
                        try {
                            getActivity().getAssets().open("public/" + resolvedAsset).close();
                            resolvedAsset = "public/" + resolvedAsset;
                        } catch (Throwable ignored) {
                        }
                    }
                }

                // Modo Lottie Animado: A view SEMPRE ocupa MATCH_PARENT x MATCH_PARENT
                // para que a tela inteira esteja 100% desimpedida para ondas de choque,
                // partículas e raios se expandirem sem qualquer corte de borda ou janela.
                lottieView = new LottieAnimationView(getActivity());
                lottieView.setSpeed(speed);

                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                );
                params.gravity = Gravity.CENTER;

                // Desativa qualquer recorte interno do Lottie ou do container Android
                try {
                    lottieView.setClipToCompositionBounds(false);
                } catch (Throwable ignored) {
                }
                splashOverlay.setClipChildren(false);
                splashOverlay.setClipToPadding(false);

                splashOverlay.addView(lottieView, params);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    lottieView.setForceDarkAllowed(false);
                }

                // Carregamento otimizado assíncrono com cache em memória (LRU)
                LottieCompositionFactory.fromAsset(getActivity(), resolvedAsset)
                    .addListener(composition -> {
                        if (isDismissing || lottieView == null) return;
                        lottieView.setComposition(composition);
                        applyAnimationSizing(composition);
                        startAnimationSequence();
                    })
                    .addFailureListener(e -> {
                        Log.e(TAG, "Falha ao carregar animação Lottie: " + e.getMessage(), e);
                        hasCompletedIntro = true;
                        setReady(true);
                    });

            } else {
                // Modo Estático (sem animação Lottie)
                hasCompletedIntro = true;
                setReady(true);
            }

        } catch (Throwable t) {
            Log.e(TAG, "Falha ao inicializar LaylaSplashScreen: " + t.getMessage(), t);
            hasCompletedIntro = true;
            setReady(true);
        }
    }

    /**
     * Aplica o dimensionamento da animação preservando a tela inteira sem recortes.
     * Quando sizeDp > 0 (ex: 260dp), o elemento central da animação mantém o tamanho
     * exato configurado, mas através de ScaleType.MATRIX o canvas da View cobre toda
     * a tela (MATCH_PARENT x MATCH_PARENT), permitindo que efeitos expansivos corram
     * livremente até qualquer borda da tela.
     */
    private void applyAnimationSizing(LottieComposition composition) {
        if (lottieView == null || getActivity() == null || composition == null) return;

        if (sizeDp > 0) {
            lottieView.setScaleType(ImageView.ScaleType.MATRIX);

            Runnable updateMatrix = () -> {
                if (lottieView == null || getActivity() == null) return;

                int viewWidth = lottieView.getWidth();
                int viewHeight = lottieView.getHeight();

                if (viewWidth <= 0 || viewHeight <= 0) {
                    DisplayMetrics dm = getActivity().getResources().getDisplayMetrics();
                    viewWidth = dm.widthPixels;
                    viewHeight = dm.heightPixels;
                }

                float density = getActivity().getResources().getDisplayMetrics().density;
                float targetSizePx = sizeDp * density;

                Rect compBounds = composition.getBounds();
                float compW = (compBounds != null && compBounds.width() > 0) ? (float) compBounds.width() : 512f;
                float compH = (compBounds != null && compBounds.height() > 0) ? (float) compBounds.height() : 512f;

                float scale = targetSizePx / Math.max(compW, compH);
                float renderedW = compW * scale;
                float renderedH = compH * scale;

                float dx = (viewWidth - renderedW) / 2.0f;
                float dy = (viewHeight - renderedH) / 2.0f;

                Matrix matrix = new Matrix();
                matrix.setScale(scale, scale);
                matrix.postTranslate(dx, dy);

                lottieView.setImageMatrix(matrix);
            };

            updateMatrix.run();

            lottieView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                int w = right - left;
                int h = bottom - top;
                int oldW = oldRight - oldLeft;
                int oldH = oldBottom - oldTop;
                if (w != oldW || h != oldH) {
                    updateMatrix.run();
                }
            });
        } else {
            lottieView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        }
    }

    private void startAnimationSequence() {
        if (isDismissing || lottieView == null) return;

        final boolean hasIntro = (introStartFrame >= 0 && introEndFrame > introStartFrame);
        final boolean hasLoopRange = ("range".equals(loopMode) && loopStartFrame >= 0 && loopEndFrame > loopStartFrame);
        final boolean hasFullLoop = "full".equals(loopMode);

        if (hasIntro && hasLoopRange) {
            // Modo 1: Reprodução contínua e fluida: intro [0, 130] seguida de loop [130, 310]
            lottieView.setMinAndMaxFrame(introStartFrame, loopEndFrame);
            lottieView.setRepeatCount(LottieDrawable.INFINITE);

            lottieView.addAnimatorUpdateListener(animation -> {
                if (isDismissing || lottieView == null) return;
                float currentFrame = lottieView.getFrame();

                if (currentFrame >= introEndFrame) {
                    if (!hasCompletedIntro) {
                        hasCompletedIntro = true;
                        lottieView.setMinFrame(loopStartFrame);

                        if (isAppReady || autoDismiss) {
                            dismissSplashWithFade();
                        }
                    }
                }
            });
        } else if (hasFullLoop) {
            // Modo 2: Loop completo da animação do início ao fim indefinidamente
            hasCompletedIntro = true;
            lottieView.setRepeatCount(LottieDrawable.INFINITE);
            lottieView.addAnimatorUpdateListener(animation -> {
                if (isDismissing || lottieView == null) return;
                if (isAppReady || autoDismiss) {
                    dismissSplashWithFade();
                }
            });
        } else if (hasLoopRange) {
            // Modo 3: Loop apenas no intervalo especificado
            hasCompletedIntro = true;
            lottieView.setMinAndMaxFrame(loopStartFrame, loopEndFrame);
            lottieView.setRepeatCount(LottieDrawable.INFINITE);
            lottieView.addAnimatorUpdateListener(animation -> {
                if (isDismissing || lottieView == null) return;
                if (isAppReady || autoDismiss) {
                    dismissSplashWithFade();
                }
            });
        } else if (hasIntro) {
            // Modo 4: Toca a introdução 1 única vez
            lottieView.setMinAndMaxFrame(introStartFrame, introEndFrame);
            lottieView.setRepeatCount(0);
            lottieView.addAnimatorListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    hasCompletedIntro = true;
                    if (isAppReady || autoDismiss) {
                        dismissSplashWithFade();
                    }
                }
            });
        } else {
            // Modo 5: Toca a animação inteira 1x
            hasCompletedIntro = true;
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

        lottieView.playAnimation();

        // Sinaliza que o Lottie já iniciou o desenho na tela, dispensando o splash nativo do Android
        setReady(true);
    }

    @PluginMethod
    public void notifyAppReady(PluginCall call) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                isAppReady = true;
                // Só fecha a splash screen se a fase de introdução (quadro 130) já tiver sido concluída
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

