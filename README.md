# Matte3D

A small native Android 3D editor prototype inspired by the mobile workflow of Prisma3D.

## Current features

- Native Java Android app (no WebView)
- OpenGL ES 3.0 viewport
- Forced landscape orientation
- Immersive fullscreen mode
- Matte gray editor interface
- Scene hierarchy
- Add Cube and Plane primitives
- Select, duplicate and delete objects
- Position / rotation / scale inspector with live sliders
- Orbit camera by dragging
- Pinch to zoom
- Reset camera view
- Local scene persistence with SharedPreferences + JSON
- GitHub Actions APK build

## Build

The repository includes a GitHub Actions workflow that builds `app-debug.apk` on pushes to `main`.

Locally, with Android SDK 35 and Gradle 8.10.2 installed:

```bash
gradle :app:assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```
