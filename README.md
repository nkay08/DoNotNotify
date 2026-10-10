# DoNotNotify

An Android app that filters and blocks unwanted notifications using customizable rules.

## Features

- **Notification Blocking** - Block notifications in real-time using Android's NotificationListenerService
- **Flexible Rules** - Create denylist (block matching), allowlist (allow only matching), or stack rules
- **Notification Stacking** - STACK rules don't block; they collapse chatty apps into a single expandable notification group so you keep the notifications without the clutter
- **Notification Proxy** - DENYLIST/ALLOWLIST rules can re-post allowed notifications as DoNotNotify's own (same title and text). Point your watch companion app (e.g. Garmin Connect) at DoNotNotify instead of the source app, and your content-based rules control exactly what reaches the watch
- **Notification Cooldown** - DENYLIST/ALLOWLIST rules can throttle an app: only the first unblocked notification passes, then further notifications are blocked until the cooldown window (seconds or minutes) runs out. Combines with proxying so a watch only gets alerted at the configured interval
- **Pattern Matching** - Match notification title/text using simple contains or regex patterns
- **Time-Based Rules** - Schedule rules to activate only during specific time windows
- **Prebuilt Rules** - 40+ pre-configured rules for popular apps (e-commerce promos, social media, etc.)
- **Auto-Install Rules** - Automatically adds relevant prebuilt rules when you install supported apps
- **Notification History** - View all received notifications with configurable retention
- **Blocked History** - Track which notifications were blocked and by which rules
- **Import/Export** - Backup and restore your rules as JSON files
- **Fully Offline** - No network permissions, no data collection

## Requirements

- Android 7.0 (API 24) or higher
- Notification listener permission

## Building

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Install debug build on connected device
./gradlew installDebug
```

### Building with podman (no local Android toolchain)

Everything below runs from the repository root. Only podman, curl, and unzip are needed on the host. The Android SDK, Gradle distribution, and dependency cache live under `.build/` (gitignored). Network access is required for the one-time setup and the first build.

**One-time setup:** pull the JDK image and install the Android SDK into `.build/sdk`:

```bash
podman pull docker.io/library/eclipse-temurin:17-jdk

mkdir -p .build/sdk
curl -fsSL -o /tmp/cmdline-tools.zip \
  https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
unzip -q -o /tmp/cmdline-tools.zip -d .build/sdk/cmdline-tools
mv .build/sdk/cmdline-tools/cmdline-tools .build/sdk/cmdline-tools/latest

podman run --rm --userns=keep-id --user 0 \
  -v "$PWD/.build/sdk:/opt/android-sdk" \
  docker.io/library/eclipse-temurin:17-jdk sh -c \
  "yes | /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/opt/android-sdk \
    'platform-tools' 'platforms;android-36' 'build-tools;36.0.0'"
```

**Every build** uses the same container command, only the Gradle task changes:

```bash
mkdir -p .build/gradle .build/apk

# Unit tests
podman run --rm --userns=keep-id --user 0 \
  -v "$PWD:/ws" \
  -v "$PWD/.build/gradle:/root/.gradle" \
  -v "$PWD/.build/sdk:/opt/android-sdk" \
  -e ANDROID_HOME=/opt/android-sdk \
  -e ANDROID_SDK_ROOT=/opt/android-sdk \
  -e GRADLE_USER_HOME=/root/.gradle \
  -w /ws \
  docker.io/library/eclipse-temurin:17-jdk ./gradlew --no-daemon test

# Debug APK (copied to .build/apk/app-debug.apk)
podman run --rm --userns=keep-id --user 0 \
  -v "$PWD:/ws" \
  -v "$PWD/.build/gradle:/root/.gradle" \
  -v "$PWD/.build/sdk:/opt/android-sdk" \
  -e ANDROID_HOME=/opt/android-sdk \
  -e ANDROID_SDK_ROOT=/opt/android-sdk \
  -e GRADLE_USER_HOME=/root/.gradle \
  -w /ws \
  docker.io/library/eclipse-temurin:17-jdk sh -c \
  "./gradlew --no-daemon assembleDebug && cp app/build/outputs/apk/debug/app-debug.apk /ws/.build/apk/"

# Release APK (copied to .build/apk/app-release.apk; signed with the public
# GitHub key unless a Play Store key is configured in local.properties)
podman run --rm --userns=keep-id --user 0 \
  -v "$PWD:/ws" \
  -v "$PWD/.build/gradle:/root/.gradle" \
  -v "$PWD/.build/sdk:/opt/android-sdk" \
  -e ANDROID_HOME=/opt/android-sdk \
  -e ANDROID_SDK_ROOT=/opt/android-sdk \
  -e GRADLE_USER_HOME=/root/.gradle \
  -w /ws \
  docker.io/library/eclipse-temurin:17-jdk sh -c \
  "./gradlew --no-daemon assembleRelease && cp app/build/outputs/apk/release/app-release.apk /ws/.build/apk/"
```

Notes:

- The first run downloads the Gradle distribution and all dependencies into `.build/gradle`; later runs are offline-friendly.
- The workdir is the repository root mounted at `/ws`, so run the commands from the project root.
- Installed SDK versions above match `compileSdk = 36`; bump them if `app/build.gradle.kts` changes.

## How It Works

1. Grant notification listener permission when prompted
2. View incoming notifications in the **History** tab
3. Press a notification to create a blocking rule, or go to **Rules** tab to browse prebuilt rules
4. Blocked notifications appear in the **Blocked** tab
5. Tap any blocked notification to view details or edit the rule that blocked it

## Documentation

Detailed documentation for developers and contributors is available in the [`docs/`](docs/) directory:

- **[Architecture & Codebase Overview](docs/ARCHITECTURE.md)** - Project structure, data models, core services, storage layer, UI layer, data flow diagrams, navigation map, and class dependencies
- **[API Reference](docs/API_REFERENCE.md)** - Method-level reference for every class and composable
- **[Developer Guide](docs/DEVELOPER_GUIDE.md)** - Practical guide for adding features, screens, rules, storage, and running tests

## License

MIT License - see [LICENSE](LICENSE) for details.
