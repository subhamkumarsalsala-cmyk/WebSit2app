package com.webwrapper.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int FILE_REQ = 1001, PERM_REQ = 2001, GEO_REQ = 2002;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final String DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private Config cfg;
    private SharedPreferences sp;
    private FrameLayout root, fullscreenBox;
    private SwipeRefreshLayout swipe;
    private WebView web;
    private ProgressBar bar;
    private LinearLayout bottomBar;
    private TextView fab, splash;
    private View customView;
    private WebChromeClient.CustomViewCallback customCb;
    private String failedUrl, defaultUa;
    private boolean offlineShown;
    private Boolean lastDesktop = null;
    private long lastBack;
    private int themeColor;
    private ValueCallback<Uri[]> fileCb;
    private PermissionRequest pendingPerm;
    private GeolocationPermissions.Callback geoCb;
    private String geoOrigin;
    private ConnectivityManager.NetworkCallback netCb;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        cfg = Config.load(this);
        sp = PreferenceManager.getDefaultSharedPreferences(this);
        PreferenceManager.setDefaultValues(this, R.xml.prefs, false);
        if (!sp.getBoolean("seeded", false)) {
            sp.edit().putBoolean("seeded", true)
                    .putBoolean("desktop_mode", cfg.desktopMode)
                    .putBoolean("fullscreen", cfg.fullscreen)
                    .putBoolean("keep_screen_on", cfg.keepScreenOn)
                    .putBoolean("pull_refresh", cfg.pullToRefresh)
                    .putBoolean("external_browser", cfg.externalInBrowser)
                    .apply();
        }
        try { themeColor = Color.parseColor(cfg.themeColor); } catch (Exception e) { themeColor = Color.parseColor("#2563EB"); }
        failedUrl = cfg.siteUrl;

        buildUi();
        setupWeb();
        applyPrefs();
        registerNetwork();

        if (b != null) web.restoreState(b); else web.loadUrl(cfg.siteUrl);
        root.postDelayed(this::hideSplash, 5000);
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private TextView barBtn(String glyph, String desc, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(glyph);
        t.setTextSize(26);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(themeColor);
        t.setContentDescription(desc);
        t.setOnClickListener(l);
        bottomBar.addView(t, new LinearLayout.LayoutParams(0, MATCH, 1f));
        return t;
    }

    private void buildUi() {
        root = new FrameLayout(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        web = new WebView(this);
        swipe = new SwipeRefreshLayout(this);
        swipe.addView(web, new ViewGroup.LayoutParams(MATCH, MATCH));
        swipe.setColorSchemeColors(themeColor);
        swipe.setOnChildScrollUpCallback((parent, child) -> web.getScrollY() > 0);
        swipe.setOnRefreshListener(() -> {
            if (offlineShown) web.loadUrl(failedUrl); else web.reload();
        });
        col.addView(swipe, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundColor(Color.parseColor("#F7F7F7"));
        barBtn("\u2039", "Back", v -> { if (web.canGoBack()) web.goBack(); });
        barBtn("\u203A", "Forward", v -> { if (web.canGoForward()) web.goForward(); });
        barBtn("\u2302", "Home", v -> web.loadUrl(cfg.siteUrl));
        barBtn("\u21BB", "Refresh", v -> { if (offlineShown) web.loadUrl(failedUrl); else web.reload(); });
        barBtn("\u22EE", "Menu", this::showMenu);
        col.addView(bottomBar, new LinearLayout.LayoutParams(MATCH, dp(52)));
        root.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.getProgressDrawable().setColorFilter(themeColor, android.graphics.PorterDuff.Mode.SRC_IN);
        root.addView(bar, new FrameLayout.LayoutParams(MATCH, dp(3)));

        fab = new TextView(this);
        fab.setText("\u22EE");
        fab.setTextColor(Color.WHITE);
        fab.setTextSize(22);
        fab.setGravity(Gravity.CENTER);
        fab.setAlpha(0.75f);
        fab.setContentDescription("Menu");
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(themeColor);
        fab.setBackground(g);
        fab.setOnClickListener(this::showMenu);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.BOTTOM | Gravity.END);
        flp.setMargins(0, 0, dp(14), dp(18));
        root.addView(fab, flp);

        fullscreenBox = new FrameLayout(this);
        fullscreenBox.setBackgroundColor(Color.BLACK);
        fullscreenBox.setVisibility(View.GONE);
        root.addView(fullscreenBox, new FrameLayout.LayoutParams(MATCH, MATCH));

        splash = new TextView(this);
        splash.setText(cfg.appName);
        splash.setTextColor(Color.WHITE);
        splash.setTextSize(26);
        splash.setGravity(Gravity.CENTER);
        splash.setBackgroundColor(themeColor);
        splash.setClickable(true);
        root.addView(splash, new FrameLayout.LayoutParams(MATCH, MATCH));

        setContentView(root);
    }

    private void hideSplash() {
        if (splash != null && splash.getVisibility() == View.VISIBLE) {
            splash.animate().alpha(0f).setDuration(300).withEndAction(() -> splash.setVisibility(View.GONE));
        }
    }

    private void setupWeb() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setGeolocationEnabled(true);
        defaultUa = s.getUserAgentString();

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void retry() { runOnUiThread(() -> web.loadUrl(failedUrl)); }
        }, "AppBridge");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                String sch = u.getScheme() == null ? "" : u.getScheme();
                if (sch.equals("http") || sch.equals("https")) {
                    if (sp.getBoolean("external_browser", false) && !sameSite(u)) {
                        openExternal(u);
                        return true;
                    }
                    return false;
                }
                openExternal(u);
                return true;
            }

            @Override
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap f) {
                offlineShown = url != null && url.startsWith("file:///android_asset");
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                swipe.setRefreshing(false);
                hideSplash();
                CookieManager.getInstance().flush();
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) {
                    failedUrl = r.getUrl().toString();
                    offlineShown = true;
                    v.loadUrl("file:///android_asset/offline.html");
                }
            }

            @Override
            public void onReceivedSslError(WebView v, SslErrorHandler h, SslError e) {
                h.cancel();
                Toast.makeText(MainActivity.this, "Secure connection fail ho gaya", Toast.LENGTH_LONG).show();
            }

            @Override
            public boolean onRenderProcessGone(WebView v, android.webkit.RenderProcessGoneDetail d) {
                recreate();
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                bar.setProgress(p);
                bar.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try {
                    startActivityForResult(params.createIntent(), FILE_REQ);
                } catch (Exception e) {
                    fileCb = null;
                    Toast.makeText(MainActivity.this, "File chooser nahi khula", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback cb) {
                if (customView != null) { cb.onCustomViewHidden(); return; }
                customView = view;
                customCb = cb;
                fullscreenBox.addView(view);
                fullscreenBox.setVisibility(View.VISIBLE);
                setImmersive(true);
            }

            @Override
            public void onHideCustomView() { hideCustom(); }

            @Override
            public boolean onJsAlert(WebView v, String url, String msg, JsResult res) {
                if (isFinishing()) { res.cancel(); return true; }
                new AlertDialog.Builder(MainActivity.this).setMessage(msg)
                        .setPositiveButton(android.R.string.ok, (d, w) -> res.confirm())
                        .setOnCancelListener(d -> res.cancel()).show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView v, String url, String msg, JsResult res) {
                if (isFinishing()) { res.cancel(); return true; }
                new AlertDialog.Builder(MainActivity.this).setMessage(msg)
                        .setPositiveButton(android.R.string.ok, (d, w) -> res.confirm())
                        .setNegativeButton(android.R.string.cancel, (d, w) -> res.cancel())
                        .setOnCancelListener(d -> res.cancel()).show();
                return true;
            }

            @Override
            public void onPermissionRequest(PermissionRequest r) {
                runOnUiThread(() -> handlePerm(r));
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    cb.invoke(origin, true, false);
                } else {
                    geoCb = cb;
                    geoOrigin = origin;
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION}, GEO_REQ);
                }
            }
        });

        web.setDownloadListener((url, ua, cd, mime, len) -> {
            try {
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                String name = URLUtil.guessFileName(url, cd, mime);
                String ck = CookieManager.getInstance().getCookie(url);
                if (ck != null) req.addRequestHeader("Cookie", ck);
                if (ua != null) req.addRequestHeader("User-Agent", ua);
                if (mime != null) req.setMimeType(mime);
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(req);
                Toast.makeText(this, "Download shuru: " + name, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                openExternal(Uri.parse(url));
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void applyPrefs() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(sp.getBoolean("javascript", true));
        boolean block = sp.getBoolean("block_images", false);
        s.setLoadsImagesAutomatically(!block);
        s.setBlockNetworkImage(block);
        try { s.setTextZoom(Integer.parseInt(sp.getString("text_zoom", "100"))); } catch (Exception ignored) { }
        s.setSupportZoom(sp.getBoolean("pinch_zoom", true));
        s.setBuiltInZoomControls(sp.getBoolean("pinch_zoom", true));
        s.setDisplayZoomControls(false);
        if (Build.VERSION.SDK_INT >= 29) {
            s.setForceDark(sp.getBoolean("force_dark", false) ? WebSettings.FORCE_DARK_ON : WebSettings.FORCE_DARK_OFF);
        }
        boolean desktop = sp.getBoolean("desktop_mode", false);
        s.setUserAgentString(desktop ? DESKTOP_UA : defaultUa);
        if (lastDesktop != null && lastDesktop != desktop) web.reload();
        lastDesktop = desktop;

        if (sp.getBoolean("keep_screen_on", false))
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        getWindow().setStatusBarColor(themeColor);
        setImmersive(sp.getBoolean("fullscreen", false));

        boolean bb = sp.getBoolean("bottom_bar", false);
        bottomBar.setVisibility(bb ? View.VISIBLE : View.GONE);
        fab.setVisibility(!bb && sp.getBoolean("menu_button", true) ? View.VISIBLE : View.GONE);
        swipe.setEnabled(sp.getBoolean("pull_refresh", true));

        switch (cfg.orientation) {
            case "portrait": setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); break;
            case "landscape": setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE); break;
            default: setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    @SuppressWarnings("deprecation")
    private void setImmersive(boolean on) {
        View d = getWindow().getDecorView();
        d.setSystemUiVisibility(on
                ? View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                : View.SYSTEM_UI_FLAG_VISIBLE);
    }

    private void hideCustom() {
        if (customView == null) return;
        fullscreenBox.removeView(customView);
        fullscreenBox.setVisibility(View.GONE);
        customView = null;
        if (customCb != null) customCb.onCustomViewHidden();
        customCb = null;
        setImmersive(sp.getBoolean("fullscreen", false));
    }

    private String strip(String h) {
        if (h == null) return "";
        h = h.toLowerCase();
        return h.startsWith("www.") ? h.substring(4) : h;
    }

    private boolean sameSite(Uri u) {
        String h = strip(u.getHost());
        String home = strip(Uri.parse(cfg.siteUrl).getHost());
        return h.equals(home) || h.endsWith("." + home);
    }

    private void openExternal(Uri u) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, u)); }
        catch (Exception e) { Toast.makeText(this, "Open nahi ho paya", Toast.LENGTH_SHORT).show(); }
    }

    private String currentUrl() {
        String u = offlineShown ? failedUrl : web.getUrl();
        return u == null ? cfg.siteUrl : u;
    }

    private void showMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        Menu m = pm.getMenu();
        m.add(0, 1, 0, "Share link");
        m.add(0, 2, 1, "Copy link");
        m.add(0, 3, 2, "Open in browser");
        MenuItem dm = m.add(0, 4, 3, "Desktop mode");
        dm.setCheckable(true);
        dm.setChecked(sp.getBoolean("desktop_mode", false));
        m.add(0, 5, 4, "Settings");
        m.add(0, 6, 5, "Exit");
        pm.setOnMenuItemClickListener(i -> {
            switch (i.getItemId()) {
                case 1:
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("text/plain");
                    send.putExtra(Intent.EXTRA_TEXT, currentUrl());
                    startActivity(Intent.createChooser(send, "Share link"));
                    break;
                case 2:
                    ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                            .setPrimaryClip(ClipData.newPlainText("link", currentUrl()));
                    Toast.makeText(this, "Link copy ho gaya", Toast.LENGTH_SHORT).show();
                    break;
                case 3: openExternal(Uri.parse(currentUrl())); break;
                case 4:
                    sp.edit().putBoolean("desktop_mode", !sp.getBoolean("desktop_mode", false)).apply();
                    applyPrefs();
                    break;
                case 5: startActivity(new Intent(this, SettingsActivity.class)); break;
                case 6: finish(); break;
            }
            return true;
        });
        pm.show();
    }

    private void handlePerm(PermissionRequest r) {
        List<String> missing = new ArrayList<>();
        for (String res : r.getResources()) {
            String p = null;
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res)) p = Manifest.permission.CAMERA;
            else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res)) p = Manifest.permission.RECORD_AUDIO;
            if (p != null && checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        }
        if (missing.isEmpty()) { r.grant(r.getResources()); return; }
        pendingPerm = r;
        requestPermissions(missing.toArray(new String[0]), PERM_REQ);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        boolean ok = results.length > 0;
        for (int r : results) if (r != PackageManager.PERMISSION_GRANTED) ok = false;
        if (code == PERM_REQ && pendingPerm != null) {
            if (ok) pendingPerm.grant(pendingPerm.getResources()); else pendingPerm.deny();
            pendingPerm = null;
        } else if (code == GEO_REQ && geoCb != null) {
            boolean any = false;
            for (int r : results) if (r == PackageManager.PERMISSION_GRANTED) any = true;
            geoCb.invoke(geoOrigin, any, false);
            geoCb = null;
        }
    }

    private void registerNetwork() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            netCb = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network n) {
                    runOnUiThread(() -> { if (offlineShown) web.loadUrl(failedUrl); });
                }
            };
            cm.registerDefaultNetworkCallback(netCb);
        } catch (Exception e) { netCb = null; }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        if (code == FILE_REQ && fileCb != null) {
            fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            fileCb = null;
        } else {
            super.onActivityResult(code, result, data);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus && customView == null) setImmersive(sp.getBoolean("fullscreen", false));
    }

    @Override
    protected void onResume() { super.onResume(); web.onResume(); applyPrefs(); }

    @Override
    protected void onPause() { super.onPause(); web.onPause(); CookieManager.getInstance().flush(); }

    @Override
    protected void onDestroy() {
        try {
            if (netCb != null)
                ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE)).unregisterNetworkCallback(netCb);
        } catch (Exception ignored) { }
        if (web != null) web.destroy();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle o) { super.onSaveInstanceState(o); web.saveState(o); }

    @Override
    public void onBackPressed() {
        if (customView != null) { hideCustom(); return; }
        if (web.canGoBack()) { web.goBack(); return; }
        if (sp.getBoolean("exit_confirm", true)) {
            long now = System.currentTimeMillis();
            if (now - lastBack > 2000) {
                lastBack = now;
                Toast.makeText(this, "Exit karne ke liye dobara back dabao", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        super.onBackPressed();
    }
}
