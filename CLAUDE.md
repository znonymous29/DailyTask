# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

DailyTask is an Android automation app for automatic clock-in/out (打卡) using accessibility services and notification monitoring. Written in Kotlin/Java with Gradle build system.

**Important Context:** This app is designed for personal/internal use only, not for commercial or illegal purposes. It automates attendance tracking by monitoring notifications and launching target apps (currently DingTalk). The app requires notification listener service, floating window permissions, and foreground service to remain alive.

## Development Commands

### Building
- Build debug APK: `./gradlew assembleDailyDebug`
- Build release APK: `./gradlew assembleDailyRelease`
- Clean build: `./gradlew clean`
- Install debug APK to connected device: `./gradlew installDailyDebug`

### Testing
- No automated tests are currently configured in the project.

### Linting
- Run Android lint: `./gradlew lint`

### Signing
Release builds are signed with a keystore file `DailyTask.jks` (stored in `app/` directory). Passwords are defined in `app/build.gradle`. For CI/CD, GitHub Actions uses secrets for signing.

## Architecture

### High-Level Structure
The app follows a typical Android MVVM-like pattern with separate layers:

1. **Application Layer**: `DailyTaskApplication` - Singleton application class initializes Room database, shared preferences, and Bugly crash reporting.

2. **UI Layer** (`com.pengxh.daily.app.ui`):
   - `MainActivity`: Primary activity managing task list, start/stop execution, and mask view (pseudo screen-off mode).
   - `SettingsActivity`, `EmailConfigActivity`, `TaskConfigActivity`, `QuestionAndAnswerActivity`, `NoticeRecordActivity` - Configuration and info screens.

3. **Service Layer** (`com.pengxh.daily.app.service`):
   - `NotificationMonitorService`: Listens for notifications from target apps and processes remote commands (电量, 启动, 停止, etc.).
   - `FloatingWindowService`: Displays a floating window for quick access and countdown display.
   - `ForegroundRunningService`: Keeps the app alive as a foreground service.
   - `CountDownTimerService`: Manages countdown timers for task execution.

4. **Data Layer** (`com.pengxh.daily.app.sqlite`):
   - Room database (`DailyTaskDataBase`) with entities: `DailyTaskBean`, `EmailConfigBean`, `NotificationBean`.
   - `DatabaseWrapper`: Centralized data access utility for all database operations.
   - Shared preferences managed via `SaveKeyValues` utility from external library.

5. **Utilities** (`com.pengxh.daily.app.utils`):
   - `Constant`: Application constants and target app package names.
   - `BroadcastManager`: Internal broadcast communication system.
   - `EmailManager`: Sends email notifications via SMTP (QQ邮箱 only).
   - `MessageType`: Enum defining message types for broadcast communication.
   - `DailyTask`: Core task scheduling logic.
   - `LogFileManager`: Logs to file for debugging.

6. **Extensions** (`com.pengxh.daily.app.extensions`): Kotlin extension functions.

7. **Event Bus**: Uses `EventBus` for component communication (e.g., `FloatViewTimerEvent`).

### Key Flows
1. **Task Execution**: MainActivity posts `dailyTaskRunnable` that iterates through scheduled times, calculates time differences, and triggers `CountDownTimerService`.
2. **Notification Processing**: `NotificationMonitorService` intercepts notifications from target apps (钉钉, 微信, QQ, etc.) and executes commands or records attendance.
3. **Remote Control**: Users can send SMS-like commands via messaging apps (微信, QQ,支付宝, TIM) to control the app (启动, 停止, 电量, etc.).
4. **Pseudo Screen-Off**: Mask view with clock display that moves randomly to prevent screen burn-in; activated via volume-down key or swipe gestures.

### Native Code
- **Location**: `app/src/main/cpp/`
- **Purpose**: Provides watermark text via JNI (`DailyTask.getWatermarkText()`). The C++ file contains a hardcoded UTF-8 byte array that decodes to a Chinese disclaimer about free software.
- **Build**: CMake configured in `app/build.gradle` with `externalNativeBuild`.

### Dependencies
- **Room**: Local database persistence.
- **EventBus**: Event-driven communication.
- **SmartRefreshLayout**: Pull-to-refresh functionality.
- **AndroidPicker**: Date/time picker wheels.
- **Bugly**: Crash reporting (Tencent).
- **Kotlin-lite-lib**: External utility library by the same author.

### Product Flavors
The app uses a single product flavor `daily` that randomizes the application ID suffix to avoid detection. The flavor is defined in `app/build.gradle` with `applicationId` generated via `createRandomCode()`.

## Configuration Notes

### Keystore
- Location: `app/DailyTask.jks`
- Alias: `key0`
- Store password: `123456789`
- Key password: `123456789`

**Security Note**: These passwords are hardcoded in `app/build.gradle`. For production use, consider moving to environment variables or secrets management.

### Target Apps
Currently supports DingTalk (`com.alibaba.android.rimet`) with placeholders for Feishu and WeWork (commented out). The target app can be changed via `Constant.getTargetApp()` which reads from shared preferences.

### Email Configuration
Only QQ邮箱 is supported as sender. Configuration is stored in `EmailConfigBean` via `EmailConfigActivity`.

## GitHub Actions

A workflow (`.github/workflows/android.yml`) automates building and releasing:
- Triggered on pushes to `main` branch and tags starting with `v`.
- Builds release APK with `assembleDailyRelease`.
- Signs APK using GitHub secrets (`SIGNING_KEY`, `ALIAS`, `KEY_STORE_PASSWORD`, `KEY_PASSWORD`).
- Creates GitHub Release with APK when tag is pushed.

## Common Development Tasks

### Adding a New Feature
1. Create/update relevant UI components in `ui/` package.
2. Add necessary data models in `model/` or `sqlite/bean/`.
3. Update `DatabaseWrapper` for data access.
4. Add business logic in `utils/` or extension functions.
5. Register new broadcast messages in `MessageType` if needed.
6. Update `MainActivity` or services to handle new functionality.

### Debugging
- Logs are written to file via `LogFileManager`.
- Use `Log.d` with tag `"MainActivity"` or other service tags.
- Check notification monitoring by examining `NotificationBean` records.

### Testing on Device
1. Ensure USB debugging is enabled.
2. Run `./gradlew installDailyDebug`.
3. Grant necessary permissions: overlay, notification access, accessibility services.
4. Configure email settings and add task times.

## Important Constraints

- **Minimum SDK**: 26 (Android 8.0)
- **Target SDK**: 36 (Android 15)
- **Compile SDK**: 36
- **Kotlin**: 2.3.0
- **AGP**: 8.10.0

The app is not compatible with Android versions below 8.0. It's tested up to Android 16 and HarmonyOS 4.0.