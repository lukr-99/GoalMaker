# Local development

Everything runs against a local Supabase stack in Docker, so development never touches real data.

## Tools

| Tool | Why | Check |
| --- | --- | --- |
| Docker Desktop | the local Supabase stack | `docker info` |
| Node.js 22+ | the pinned Supabase CLI (`npm install`) | `npx supabase --version` |
| Python 3.11+ | repository and migration tools | `py --version` |
| JDK 21 + Android SDK | the Android app | `android\gradlew.bat -p android --version` |
| .NET SDK 10.0.200+ | the Windows app | `dotnet --list-sdks` |
| Inno Setup 6 | the Windows installer | `ISCC.exe` under `%LOCALAPPDATA%\Programs\Inno Setup 6` |
| OpenSSL | release and signing tools | ships with Git for Windows |

## The local stack

```powershell
npm install
npx supabase start          # first run downloads the images
npx supabase status         # URLs and keys
```

GoalMaker's stack uses ports 553xx, so it runs beside other local Supabase projects:

| Service | Address |
| --- | --- |
| API | http://127.0.0.1:55321 |
| Database | postgresql://postgres:postgres@127.0.0.1:55322/postgres |
| Studio | http://127.0.0.1:55323 |
| Mail viewer (sign-in codes) | http://127.0.0.1:55324 |

`npx supabase db reset` rebuilds the database from the migrations. `npx supabase stop` stops the
stack and keeps its data; add `--no-backup` to drop it.

If Docker Desktop won't start with "The file cannot be accessed by the system" about a socket in
`%LOCALAPPDATA%\Docker\run` or `%LOCALAPPDATA%\docker-secrets-engine`, rename that folder (for
example to `run.stale`) and start Docker again; it recreates it.

## Android

```powershell
android\gradlew.bat -p android assembleDebug
powershell -File android\tools\build-and-install.ps1 -Serial emulator-5554 -Launch
```

The debug app (`com.goalmaker.app.debug`, version `X.Y.Z-dev`) talks to `http://10.0.2.2:55321`,
the PC as seen from the emulator. On a phone, open Settings → Developer and enter the PC's LAN
address (for example `http://192.168.1.20:55321`), then Save and restart. Sign in with any address
and read the code in the mail viewer.

`android/tools/phone.ps1` has serial-safe screenshots, logcat, taps and key presses.

## Windows

```powershell
dotnet build windows\GoalMaker.slnx
windows\src\GoalMaker.App\bin\Debug\net10.0-windows10.0.19041.0\GoalMaker.exe --no-activate
```

Dev builds use `http://127.0.0.1:55321` and keep their files in `%LOCALAPPDATA%\GoalMaker-dev`
(session, settings, `logs\crash.log`). Launch switches: `--tray` (start hidden), `--open
today|settings`, `--no-activate` (show without taking focus). A second launch hands its switches to
the running app.

A side-by-side dev installer ("GoalMaker Dev") for testing the installer itself:

```powershell
powershell -File windows\installer\build-installer.ps1 -Dev
```

## Testing the update channel locally

1. Make a throwaway key: `openssl ecparam -name prime256v1 -genkey -noout -out test.pem`, and its
   public key: `openssl ec -in test.pem -pubout -outform DER | base64 -w0`.
2. Build two release installers against the local stack by setting `GOALMAKER_SUPABASE_URL`,
   `GOALMAKER_SUPABASE_KEY` (the local publishable key) and `GOALMAKER_MANIFEST_PUBLIC_KEY`, changing
   `version.properties` between the builds (restore it afterwards).
3. Publish the newer one:
   `python tools/publish_release.py --version X.Y.Z --windows-installer <setup.exe> --signing-key test.pem --supabase-url http://127.0.0.1:55321 --secret-key <local secret key>`.
4. Install the older one, sign in, Settings → Check for updates → Install.
