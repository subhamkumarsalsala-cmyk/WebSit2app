# Website to App (APK)

Kisi bhi website ke link se Android app (APK) banane ka project.

## APK kaise banaye (GitHub Actions, free)
1. github.com pe free account, naya repository banao.
2. Is ZIP ki saari files upload karo (`.github` folder bhi zaroori hai).
3. **Actions** tab -> **Build APK from Website Link** -> **Run workflow**.
4. Form bharo: website link, app ka naam, package ID, theme color, orientation, aur on/off options.
5. 3-5 minute baad run kholo, **Artifacts** se `app-apk` download karo, `app-debug.apk` install karo.

Doosri website ke liye dobara Run workflow karo. **Har app ka package ID alag rakho**, tabhi ek phone me kai apps install honge.

## Custom icon
Repository ke root me `icon.png` (512x512, square) upload kar do. Build apne aap icon laga dega.

## Build-time settings (workflow form)
Link, naam, package ID, theme color, orientation, fullscreen, pull to refresh, screen on, desktop mode, external links browser me.

## In-app Settings (menu -> Settings)
Desktop mode, dark mode for websites, text size, pinch zoom, fullscreen, keep screen on, bottom bar, floating menu button, pull to refresh, external links, double-back exit, JavaScript, block images, clear cache, clear cookies.

## Features
Splash screen, loading bar, pull to refresh, offline page + auto reload jab internet aaye, file upload, download (login ke saath), camera/mic/location permission, fullscreen video, JS alert/confirm, tel:/mailto: links, share/copy/open in browser, SSL safety, crash recovery.

## Note
Ye debug APK hai. Update karne ke liye pehle purani app uninstall karni padti hai (har build ki signing key alag hoti hai).
