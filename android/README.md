# PhotoScan Android

Android app for the photoscan workflow: pick a video, extract frames, reconstruct,
then view the model and set its real-world scale from measured distances.

## Status

| Part | State |
| --- | --- |
| Video picker and frame extraction (Android `MediaMetadataRetriever`) | Built, not yet run on a device |
| Frame sampling and scale maths (`domain/`) | Built, unit tested (11 JVM tests pass) |
| 3D viewer: orbit (tilt included), pan, pinch zoom, reset (SceneView / Filament) | Built, not yet run on a device |
| Two-point calibration and measurement | Built, not yet run on a device |
| Loading the bundled sample model | Built, not yet run on a device |
| **Reconstruction (video → model)** | **Drafted, not compiled.** Native COLMAP with a JNI bridge, OpenImageIO removed. See `native/README.md`. Until `libphotoscan_native.so` is packaged, `UnavailableEngine` refuses to run |

The reconstruction step is the on-device native COLMAP port. The code is written but
not yet compiled. Build steps and known gaps are in `native/README.md`.

## Build

Requirements: JDK 17 or newer (the build was tested with JDK 21), an Android SDK
with platform 37.0, build-tools 37.0.0, and NDK 27.2 (NDK is not needed yet).

```bash
export ANDROID_HOME=$HOME/Android/Sdk      # or your SDK path
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew testDebugUnitTest assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Install it with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Versions and why

- AGP 9.3.3 on Gradle 9.5.0.
- Kotlin 2.4.21, with `android.builtInKotlin=false` and `android.newDsl=false` in
  `gradle.properties`. AGP 9's built-in Kotlin is 2.2.x, which cannot read the
  2.4 metadata in the current Compose and SceneView libraries. Remove both
  flags when those libraries are built against Kotlin 2.2 or the built-in
  compiler catches up.
- compileSdk 37.0, minSdk 26, targetSdk 36. Compose and SceneView require
  compile API 37.
- SceneView 4.67.0 for the 3D viewer. It is Filament-based and loads glTF/GLB.

## Sample model

`app/src/main/assets/sample_car.glb` is a simple car-shaped mesh for trying the
viewer. Regenerate it with:

```bash
python tools/make_sample_model.py
```

## Viewer controls

- One finger: orbit. This includes tilting the view up and down.
- Two fingers: pan and pinch to zoom.
- Reset view: returns to the starting framing.
- Calibrate: tap two points on the model, then enter their real distance in mm.
  Repeat for several pairs. The app shows the scale factor and the spread between
  references, and warns above 1%.
- Measure: after calibrating, tap two points to read their real size in mm.

The model is shown in its own units. The scale tool multiplies model distances
by the calibrated factor to get millimetres.

## Reconstruction (not yet built)

The plan is COLMAP compiled for arm64 Android with the NDK and called from Kotlin
over JNI. Checked so far:

- COLMAP source, Boost, Ceres, glog, gflags, Eigen, PoseLib and SuiteSparse are
  reachable from the build environment.
- COLMAP's image I/O requires OpenImageIO, which is a heavy dependency. Porting
  `sensor/bitmap.cc` to a lighter decoder such as stb_image is the likely route.
- COLMAP's dense stage (PatchMatch stereo, meshing) is CPU-heavy. On a phone it
  is likely to take a long time per scan.

Until that backend exists, the app stops after frame extraction and says so.
