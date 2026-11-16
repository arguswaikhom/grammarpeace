# GrammarPeace for Android ✌️

Paste or select text anywhere on your phone and get it fixed, improved, re-toned or translated. Same prompts and providers as the Chrome extension, no keyboard to install.

## Ways to use it

| Where | What happens |
|---|---|
| **The app** | Type or paste text, tap ✌️ Fix (or Improve, Tone, Translate), see the changes highlighted, then copy or share. |
| **Text selection menu** | Select text in any app and tap **GrammarPeace** in the menu next to Copy and Paste (it can sit behind the ⋮ overflow). A sheet opens over the app with the fix. In an editable field, **Replace** swaps the text in place. |
| **Share sheet** | Share text from any app to GrammarPeace. The result is copied back. |
| **Quick Settings tile** | Add the "Check clipboard" tile to your notification shade. One tap opens the app and fixes whatever you last copied. |
| **Launcher shortcut** | Long press the app icon and choose "Check clipboard". |

Every result also offers **Alternatives** (two other ways to say it) and **Explain** (what was wrong and why).

Some apps draw their own selection menu and leave out other apps' actions. In those, copy the text and use the tile or the app instead.

## Set it up

1. Install the app (see below).
2. Open it, go to Settings, pick a provider and paste your API key. Tap **Test**.
   - Claude: [console.anthropic.com](https://console.anthropic.com/settings/keys)
   - OpenAI: [platform.openai.com](https://platform.openai.com/api-keys)
   - OpenRouter (free models): [openrouter.ai](https://openrouter.ai/keys)

Your text goes straight from the phone to the provider you chose. There is no GrammarPeace server. The API key is encrypted with a key held in the Android Keystore and is left out of backups.

## Build it

You need JDK 17 or newer and the Android SDK (platform 37 and build-tools). Android Studio installs both.

```sh
cd android
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`. Install it with `adb install` or copy it to the phone and open it.

Every push that touches `android/` also builds the APK on GitHub Actions. Download it from the run's **Artifacts**.

To publish your own build on Google Play, change `applicationId` in `app/build.gradle.kts` and set up release signing.

## Test it

```sh
./gradlew testDebugUnitTest lintDebug
```

The tests run on the JVM, no phone or emulator needed. They cover the prompts, the diff, the provider client against a mock server, and the screens themselves (including the Replace hand-back) through Robolectric.

## Project layout

```
android/app/src/main/java/com/grammarpeace/app/
  core/    Prompts, models, diff and the provider client. Plain Kotlin, no Android.
  data/    Settings (DataStore) and API key encryption (Android Keystore)
  ui/      MainActivity (editor + settings), QuickActivity (selection menu and share sheet)
  tile/    Quick Settings tile
```

`core/Prompts.kt` mirrors `background.js` and `ios/Shared/Prompts.swift`. Change a prompt in one, change it in all three.
