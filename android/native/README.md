# Native reconstruction (COLMAP for Android)

Status: **drafted, not yet compiled.** Nothing in this directory has been built
yet. The steps below are the plan and need to be run and debugged on a machine with
the Android NDK 27.2 installed.

## Layout

| Path | Purpose |
| --- | --- |
| `scripts/fetch_sqlite.sh` | Extracts the SQLite amalgamation from the `better-sqlite3` npm package |
| `scripts/build_deps.sh` | Cross-compiles Eigen, gflags, glog, Ceres, PoseLib, SQLite, stb for arm64-v8a |
| `scripts/prepare_colmap.sh` | Copies COLMAP and applies the Android changes |
| `patches/sensor/bitmap.cc` | Replaces COLMAP's OpenImageIO-based image I/O with stb_image |
| `jni/photoscan_jni.cc` | JNI bridge: runs `AutomaticReconstructionController` on a folder of frames |
| `CMakeLists.txt` | Builds `libphotoscan_native.so` against the patched COLMAP tree |

## Steps

```bash
export ANDROID_NDK=$HOME/Android/Sdk/ndk/27.2.12479018
./android/native/scripts/build_deps.sh ~/android-native
./android/native/scripts/prepare_colmap.sh ~/src/colmap ~/android-native/colmap-android
cd android && ./gradlew assembleDebug -PphotoscanNative \
  -PcolmapSrc=$HOME/android-native/colmap-android \
  -PprefixPath=$HOME/android-native/install/arm64-v8a
```

## Known gaps

- **OpenImageIO is removed.** COLMAP's EXIF priors are not read, so focal length
  comes from COLMAP's default guess. Video frames have no EXIF anyway.
- **LoMa** (`feature/loma.cc`) includes OpenImageIO headers. Only the include is
  removed; whether the rest compiles with ONNX disabled is unverified.
- **Dense stage** (PatchMatch and Poisson meshing) is CPU-only and will be slow on a
  phone. Expect minutes to hours for a full orbit. The first target is a short orbit
  at reduced resolution.
- **Versions** are pinned in `build_deps.sh` (Eigen 3.4.0, glog v0.7.1, gflags v2.2.2,
  Ceres 2.2.0). PoseLib and stb are cloned from `master` and should be pinned once
  they build.
- **Not tested on a device.** The build has not been run, and the app has not been
  run on a phone.
