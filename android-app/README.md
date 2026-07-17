# Voice Timer — Android app

Native Android wrapper around the web app (`app/src/main/assets/www/index.html`,
unchanged from the top-level version) with offline speech recognition via
[Vosk](https://alphacephei.com/vosk/) instead of the browser's Web Speech API,
so the microphone doesn't repeatedly stop/restart on mobile.

## Building

The German speech model isn't tracked in git (one of its files exceeds GitHub's
100MB limit). Fetch it once before building:

```sh
./scripts/setup-model.sh
```

Then build a debug APK:

```sh
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.
