# Screen Limit

An Android app (Android 10+) that adds up how long your phone is **switched on and unlocked** each day. When the total reaches **1 h 30 min** it locks the phone, and every time you unlock it again it locks straight away, until midnight, when the count resets.

- Warnings at 10 minutes and 2 minutes left
- A small ongoing notification shows today's usage
- Never locks in the middle of a phone or video call (locks when the call ends)
- Emergency calls still work from the lock screen
- Keeps counting after a restart; the daily limit can be changed in the app (15 min steps)

## Get the APK — option A: GitHub (no software to install)

1. Create a free account at github.com, then **New repository** → name it `ScreenLimit` → **Create**.
2. Click **uploading an existing file**, drag in everything from this folder **including the hidden `.github` folder**, and **Commit**.
   (If your computer hides `.github`, create it on GitHub instead: **Add file → Create new file**, name it `.github/workflows/build.yml`, and paste in the contents of that file.)
3. Open the **Actions** tab. The "Build APK" run takes about 3–5 minutes.
4. Open the finished run, download **ScreenLimit-apk** under *Artifacts*, unzip it, and copy `app-debug.apk` to your phone.

## Get the APK — option B: Android Studio

Open this folder in Android Studio, wait for it to sync, then **Build → Build App Bundle(s) / APK(s) → Build APK(s)**, or plug in your phone and press **Run**.

## Set up on the phone

1. Open the APK and allow "Install unknown apps" when asked.
2. Open **Screen Limit** and tap **Turn on** for all three items:
   - **Permission to lock the phone** (required): this is a device-admin permission that only allows locking the screen.
   - **Warnings before locking**: allow notifications.
   - **Keep running in the background**: allow.
3. On Xiaomi / Redmi / POCO, Vivo, Oppo, Realme and OnePlus phones, also open the app's settings page and turn on **Autostart** and set Battery to **No restrictions**, otherwise the phone may stop the app in the background.

## Notes

- Counting starts when you first open the app, so the first day only counts usage from that point.
- To remove the app later: Settings → Security → Device admin apps → turn off Screen Limit, then uninstall.
- To change the default limit in code, edit `DEFAULT_LIMIT_MIN` in `app/src/main/java/app/screenlimit/Store.kt`.
