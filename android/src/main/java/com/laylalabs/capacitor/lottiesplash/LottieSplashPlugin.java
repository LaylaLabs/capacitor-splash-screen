package com.laylalabs.capacitor.lottiesplash;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
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

@CapacitorPlugin(name = "LottieSplash")
public class LottieSplashPlugin extends Plugin {
    private static final String TAG = "LottieSplashPlugin";

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
            splashOverlay = new FrameLayout(getActivity());
            try {
                splashOverlay.setBackgroundColor(Color.parseColor(backgroundColorHex));
            } catch (Throwable e) {
                splashOverlay.setBackgroundColor(Color.WHITE);
            }
            splashOverlay.setClickable(true);
            splashOverlay.setFocusable(true);

            lottieView = new LottieAnimationView(getActivity());
            lottieView.setAnimation(animationPath);
            lottieView.setSpeed(speed);

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
                            }
                        })
                        .start();
                }
            });
        }
    }
}
