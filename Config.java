package com.webwrapper.app;

import android.content.Context;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class Config {
    public String appName = "My Web App";
    public String siteUrl = "https://example.com";
    public String themeColor = "#2563EB";
    public String orientation = "auto";
    public boolean fullscreen = false;
    public boolean pullToRefresh = true;
    public boolean keepScreenOn = false;
    public boolean desktopMode = false;
    public boolean externalInBrowser = false;

    public static Config load(Context c) {
        Config k = new Config();
        try (InputStream in = c.getAssets().open("config.json")) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int n;
            while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
            JSONObject j = new JSONObject(o.toString("UTF-8"));
            k.appName = j.optString("app_name", k.appName);
            k.siteUrl = j.optString("site_url", k.siteUrl);
            k.themeColor = j.optString("theme_color", k.themeColor);
            k.orientation = j.optString("orientation", k.orientation);
            k.fullscreen = j.optBoolean("fullscreen", k.fullscreen);
            k.pullToRefresh = j.optBoolean("pull_to_refresh", k.pullToRefresh);
            k.keepScreenOn = j.optBoolean("keep_screen_on", k.keepScreenOn);
            k.desktopMode = j.optBoolean("desktop_mode", k.desktopMode);
            k.externalInBrowser = j.optBoolean("external_in_browser", k.externalInBrowser);
        } catch (Exception ignored) { }
        return k;
    }
}
