# MJ Android APK (1.1.1)

This branch packages the existing MJ HTML interface and its Java bridge as an Android application.

## Project layout

- `app/src/main/assets/index.html` — the MJ interface from release tag `1.1.1`
- `MJBridge.java` — native Android actions exposed to the web interface
- `MJAccessService.java` — optional accessibility actions
- `MainActivity.java` — Android WebView host using AndroidX WebKit's local asset loader
- `app/src/main/AndroidManifest.xml` — app permissions and service declarations

## Build

The GitHub Actions workflow **Build MJ Android APK** builds a debug APK on pushes to this branch and supports manual runs.

Build output:
`app/build/outputs/apk/debug/app-debug.apk`

Download the **MJ-1.1.1-debug-apk** artifact from the successful workflow run.

The debug APK is installable for testing. It is signed with the temporary Android debug key created by the build runner; a stable release-signing key is required for future in-place updates.

## Permissions and privacy

- Internet access is used by the web interface for its selected online AI provider and remote web content.
- Contacts and phone-call permissions are requested only when the related features need them.
- Camera permission is requested only when the flashlight is used.
- The Accessibility service is optional and remains off until the user enables it in Android Settings. When enabled, it can read visible text or interact with other apps only after a command is issued from MJ.
- Content deliberately sent to an AI provider may be processed under that provider's terms. API keys entered in the interface are stored in the WebView's local storage on the device. Do not put personal API keys in source files or commits.

## Android requirements

- Minimum Android version: Android 8.0 (API 26)
- Target SDK: 35
- Application ID: `com.mj.agent`
