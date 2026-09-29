package com.hhk16.push100;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ServiceWorkerController;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private static final String APP_URL = "https://100-push-production.up.railway.app/";
    private static final String CHANNEL_ID = "push100_rest";
    private static final int NOTIFICATION_ID = 100;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1001;

    private WebView webView;
    private CountDownTimer nativeRestTimer;
    private ToneGenerator toneGenerator;
    private boolean foreground = false;
    private long lastRestAlertMs = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(9, 10, 11));
        window.setNavigationBarColor(Color.rgb(9, 10, 11));
        window.getDecorView().setSystemUiVisibility(0);

        createNotificationChannel();
        toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(9, 10, 11));
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ServiceWorkerController.getInstance().getServiceWorkerWebSettings().setAllowContentAccess(true);
        }

        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String host = request.getUrl().getHost();
                return host != null && !host.equals("100-push-production.up.railway.app");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(
                    "window.__ANDROID_APK__=true;document.documentElement.classList.add('android-apk');",
                    null
                );
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    String html = "<!doctype html><html><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                            "<body style='margin:0;background:#090a0b;color:#f7f8f8;font-family:sans-serif;padding:32px'>" +
                            "<div style='max-width:520px;margin:60px auto'><div style='font-size:48px;font-weight:900;color:#d9ff43'>100 PUSH</div>" +
                            "<h2>You're offline</h2><p style='color:#969da7;line-height:1.5'>Connect once to load the latest workout app. After that, Android WebView and the app's offline cache will keep recent content available.</p>" +
                            "<button style='background:#d9ff43;border:0;border-radius:14px;padding:14px 18px;font-weight:900' onclick='location.href=\"" + APP_URL + "\"'>TRY AGAIN</button></div></body></html>";
                    view.loadDataWithBaseURL(APP_URL, html, "text/html", "UTF-8", null);
                }
            }
        });

        webView.loadUrl(APP_URL);
    }

    @Override
    protected void onResume() {
        super.onResume();
        foreground = true;
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onPause() {
        foreground = false;
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        cancelNativeRest();
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
        if (webView != null) {
            webView.removeJavascriptInterface("Android");
            webView.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Rest timer",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Alerts when your rest period is finished");
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 120, 60, 180});
        channel.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, null);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST
            );
        }
    }

    private void startNativeRest(int seconds) {
        cancelNativeRest();
        nativeRestTimer = new CountDownTimer(Math.max(1, seconds) * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {}

            @Override
            public void onFinish() {
                nativeRestTimer = null;
                if (!foreground) {
                    showRestNotification("Rest complete", "Your next set is ready.");
                }
            }
        };
        nativeRestTimer.start();
    }

    private void cancelNativeRest() {
        if (nativeRestTimer != null) {
            nativeRestTimer.cancel();
            nativeRestTimer = null;
        }
    }

    private void showRestNotification(String title, String message) {
        long now = System.currentTimeMillis();
        if (now - lastRestAlertMs < 2500) return;
        lastRestAlertMs = now;

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .build();

        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, notification);
    }

    private void vibrate(long... pattern) {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (pattern.length <= 1) {
            long duration = pattern.length == 0 ? 80 : pattern[0];
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        }
    }

    public class AndroidBridge {
        @JavascriptInterface
        public void requestNotifications() {
            runOnUiThread(() -> requestNotificationPermissionIfNeeded());
        }

        @JavascriptInterface
        public void startRest(int seconds) {
            runOnUiThread(() -> startNativeRest(seconds));
        }

        @JavascriptInterface
        public void cancelRest() {
            runOnUiThread(() -> cancelNativeRest());
        }

        @JavascriptInterface
        public void countdown(int seconds) {
            runOnUiThread(() -> {
                if (toneGenerator != null && foreground) {
                    toneGenerator.startTone(
                            seconds <= 1 ? ToneGenerator.TONE_PROP_BEEP2 : ToneGenerator.TONE_PROP_BEEP,
                            95
                    );
                }
                if (foreground) vibrate(35);
            });
        }

        @JavascriptInterface
        public void restComplete(String title, String message) {
            runOnUiThread(() -> {
                cancelNativeRest();
                if (foreground) {
                    if (toneGenerator != null) {
                        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 220);
                    }
                    vibrate(0, 100, 55, 150);
                } else {
                    showRestNotification(title, message);
                }
            });
        }

        @JavascriptInterface
        public void setWorkoutActive(boolean active) {
            runOnUiThread(() -> {
                if (active) {
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } else {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            });
        }
    }
}
