# Crisp Android Speech

Android TTS engine and test UI for CrispASR GGUF TTS models.

Supported model families:
- Kokoro FP16/Q8 GGUF plus Kokoro voice GGUF packs.
- VibeVoice realtime 0.5B Q8/Q4 GGUF plus VibeVoice voice prompt GGUF packs.
- Qwen3-TTS 0.6B Q8/Q4 GGUF plus Qwen3 voice/codec assets when available.

The native bridge returns mono PCM16 directly to Java. The Android TTS service streams that PCM through `SynthesisCallback`; the UI plays it with `AudioTrack` and shows timing fields from native and Java.

## CrispASR Native Dependencies

`AndroidSpeech` does not vendor or copy CrispASR. It builds the required native libraries from the sibling source tree:

```text
I:\Projects\AndroidVoice
+-- AndroidSpeech
+-- CrispASR
```

Before building the APK, prepare the native toolchain used by the CrispASR CMake build:

```powershell
# Android toolchain used for arm64-v8a native libraries.
Test-Path %userprofile%\AppData\Local\Android\Sdk\ndk\29.0.14206865
Test-Path %userprofile%\AppData\Local\Android\Sdk\cmake\4.1.2

# Vulkan SDK used by GGML Vulkan.
Test-Path C:\VulkanSDK\1.4.341.1\Bin\glslc.exe

# Host compiler used to build GGML's Vulkan shader generator on Windows.
Test-Path "C:\Program Files\LLVM\bin\clang.exe"
Test-Path "C:\Program Files\LLVM\bin\clang++.exe"
Test-Path "C:\Program Files\LLVM\bin\llvm-rc.exe"
```

No separate CrispASR install step is required. Gradle passes `CRISPASR_ROOT=I:/Projects/AndroidVoice/CrispASR` to CMake, and CMake builds the Android-needed targets from source:

- `kokoro`
- `vibevoice`
- `qwen3_tts`
- `ggml`, `ggml-base`, `ggml-cpu`, and `ggml-vulkan`
- `crisp_android_tts`, the JNI bridge used by the app and TTS service

If you moved either directory or changed the Vulkan/LLVM paths, update:

- `app/build.gradle`
- `local.properties`
- `cmake/host-vulkan-shaders-windows.cmake`

After changing native paths or toolchains, clear the generated CMake cache:

```powershell
cd I:\Projects\AndroidVoice\AndroidSpeech
Remove-Item .\app\.cxx -Recurse -Force -ErrorAction SilentlyContinue
```

## CrispASR RelWithDebInfo Build

For host-side debugging and profiling, build the sibling CrispASR tree as `RelWithDebInfo`. This keeps MSVC optimization enabled while generating PDB debug symbols for the CLI, CrispASR DLL, and GGML backends.

```powershell
cd I:\Projects\AndroidVoice\CrispASR

cmd.exe /c "call `"C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat`" && cmake -S . -B build-relwithdebinfo-vulkan -G Ninja -DCMAKE_BUILD_TYPE=RelWithDebInfo -DCMAKE_MSVC_DEBUG_INFORMATION_FORMAT=ProgramDatabase -DGGML_VULKAN=ON -DGGML_CUDA=OFF -DVulkan_INCLUDE_DIR=C:/VulkanSDK/1.4.341.1/Include -DVulkan_LIBRARY=C:/VulkanSDK/1.4.341.1/Lib/vulkan-1.lib -DVulkan_GLSLC_EXECUTABLE=C:/VulkanSDK/1.4.341.1/Bin/glslc.exe -DCRISPASR_BUILD_TESTS=OFF -DCRISPASR_BUILD_EXAMPLES=ON -DCRISPASR_BUILD_SERVER=OFF && cmake --build build-relwithdebinfo-vulkan --target crispasr-cli --parallel"
```

Main outputs:

```text
I:\Projects\AndroidVoice\CrispASR\build-relwithdebinfo-vulkan\bin\crispasr.exe
I:\Projects\AndroidVoice\CrispASR\build-relwithdebinfo-vulkan\bin\crispasr.dll
I:\Projects\AndroidVoice\CrispASR\build-relwithdebinfo-vulkan\bin\crispasr.pdb
I:\Projects\AndroidVoice\CrispASR\build-relwithdebinfo-vulkan\bin\ggml-vulkan.dll
I:\Projects\AndroidVoice\CrispASR\build-relwithdebinfo-vulkan\bin\ggml-vulkan.pdb
```

Run the optimized debug-info CLI from that output directory:

```powershell
cd I:\Projects\AndroidVoice\CrispASR
.\build-relwithdebinfo-vulkan\bin\crispasr.exe --diagnostics
.\build-relwithdebinfo-vulkan\bin\crispasr.exe --help
```

## Android App Build

```powershell
cd I:\Projects\AndroidVoice\AndroidSpeech
.\gradlew.bat assembleDebug
```

By default the native build produces `arm64-v8a`, which is the device target for Pixel 10. To build for an Android Emulator / AVD, pass `crispAbi=x86_64`:

```powershell
cd I:\Projects\AndroidVoice\AndroidSpeech
.\gradlew.bat -PcrispAbi=x86_64 assembleDebug
```

The project compiles against SDK 36 and runs on the Android 36.1 Pixel 10 AVD. It uses NDK `29.0.14206865`. `local.properties` points at:
- `%userprofile%\AppData\Local\Android\Sdk`
- `C:\VulkanSDK\1.4.341.1`
- sibling CrispASR source tree at `I:\Projects\AndroidVoice\CrispASR`

## Vulkan AVD Debugging

For emulator debugging, use the Pixel 10 AVD on the Android 36.1 Google Play `x86_64` system image and build the app with `-PcrispAbi=x86_64`. The helper scripts create/repair and launch an AVD with host GPU/Vulkan settings:

```powershell
cd I:\Projects\AndroidVoice\AndroidSpeech

# Creates/updates an AVD named Pixel_10.
# This defaults to system-images;android-36.1;google_apis_playstore;x86_64.
.\scripts\create-vulkan-avd.ps1

# Start the emulator with host GPU and Vulkan enabled.
.\scripts\run-vulkan-avd.ps1 -NoSnapshot

# In another terminal, wait for boot and install the x86_64 debug APK.
%userprofile%\AppData\Local\Android\Sdk\platform-tools\adb.exe wait-for-device
.\scripts\install-debug-avd.ps1
```

If your SDK tools still do not expose a `pixel_10` hardware profile through `avdmanager list device`, `create-vulkan-avd.ps1` keeps the AVD name `Pixel_10` but falls back to the `pixel_9` hardware profile so the Android 36.1 image can boot. Pass an explicit profile if you add a valid Pixel 10 hardware profile later:

```powershell
.\scripts\create-vulkan-avd.ps1 -Device "pixel_10"
```

Check Vulkan visibility from the emulator:

```powershell
%userprofile%\AppData\Local\Android\Sdk\platform-tools\adb.exe shell cmd gpu vkjson
%userprofile%\AppData\Local\Android\Sdk\platform-tools\adb.exe shell getprop | Select-String -Pattern "vulkan|gpu|renderer"
```

AVD notes:
- Keep the Pixel 10 / physical-device path on `arm64-v8a`.
- Use `x86_64` for the emulator unless you deliberately created an arm64 AVD.
- The app declares Vulkan as optional, so CPU debugging still works if the emulator cannot expose Vulkan on a given host GPU/driver.
- GGML Vulkan shader generation requires `C:\VulkanSDK\1.4.341.1\Bin\glslc.exe` and the host LLVM toolchain listed above.

## Device Testing

Install and push GGUF files to the shared model directory:

```powershell
.\gradlew.bat installDebug
.\scripts\push-models.ps1 -Source ..\CrispASR
.\gradlew.bat connectedDebugAndroidTest
```

By default `push-models.ps1` writes to:

```text
/sdcard/Android/media/com.crispasr.androidspeech/CrispASR
```

That location is visible to the app and survives normal APK reinstalls, so repeated installs do not require copying multi-GB GGUF files again. The script also skips files that already exist remotely with the same byte size. Use `-Force` to recopy.

FP16 GGUF files are skipped by default to save emulator/device storage and RAM pressure. Pass `-IncludeFp16` only when you want to test FP16 explicitly:

```powershell
.\scripts\push-models.ps1 -Source ..\CrispASR -IncludeFp16
```

On the AVD path, install and push models in one step:

```powershell
.\scripts\install-debug-avd.ps1 -PushModels
```

`install-debug-avd.ps1 -PushModels` uses the same shared model directory and only copies missing or size-mismatched quantized GGUF files. Add `-IncludeFp16` to include FP16 files, or `-ForceModelCopy` to recopy even when a matching remote file exists.

The older app-private model directory is still scanned for compatibility:

```text
/sdcard/Android/data/com.crispasr.androidspeech/files/CrispASR
```

Use `.\scripts\push-models.ps1 -TargetKind AppExternal -ResetModelDirectory` only when specifically debugging app-private external storage ownership issues.

## App Logcat

Use a dedicated terminal for logcat while running the app. Clear old logs first so timing output from the current Speak press is easy to read:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$serial = "emulator-5554"

& $adb -s $serial logcat -c
& $adb -s $serial logcat -v time CrispNativeTts:I CrispTtsUi:I CrispModelCatalog:I AndroidRuntime:E DEBUG:E '*:S'
```

For a physical Pixel 10, omit `-s $serial` when only one device is connected:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb logcat -c
& $adb logcat -v time CrispNativeTts:I CrispTtsUi:I CrispModelCatalog:I AndroidRuntime:E DEBUG:E '*:S'
```

Useful tags:
- `CrispModelCatalog`: scanned model roots, model/voice counts, scan timing.
- `CrispNativeTts`: native model load, voice load, synthesis, PCM conversion, backend, and error messages.
- `CrispTtsUi`: Java-side total timing and UI playback status.
- `AndroidRuntime` and `DEBUG`: Java/native crashes.

After pressing **Speak**, look for `native synth start`, `native synth done`, `synthesize ok=true`, and the timing fields `loadMs`, `voiceMs`, `synthMs`, `pcmConvertMs`, `nativeTotalMs`, and `javaTotalMs`.

On device, open **Crisp Android Speech**, select model, optional voice, CPU or Vulkan, then press **Speak**.
