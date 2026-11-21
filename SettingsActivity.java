package com.webwrapper.app;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.widget.Toast;

@SuppressWarnings("deprecation")
public class SettingsActivity extends PreferenceActivity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        addPreferencesFromResource(R.xml.prefs);

        findPreference("clear_cache").setOnPreferenceClickListener(p -> {
            new WebView(this).clearCache(true);
            WebStorage.getInstance().deleteAllData();
            Toast.makeText(this, "Cache saaf ho gaya", Toast.LENGTH_SHORT).show();
            return true;
        });
        findPreference("clear_cookies").setOnPreferenceClickListener(p -> {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
            Toast.makeText(this, "Cookies hat gayi", Toast.LENGTH_SHORT).show();
            return true;
        });
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            findPreference("about").setSummary(Config.load(this).appName + " " + v);
        } catch (Exception ignored) { }
    }
}
