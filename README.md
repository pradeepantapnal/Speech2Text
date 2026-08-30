# Speech2Text V0.2

Android 12+ ARM64 transcription and benchmarking with Sherpa-ONNX and
Moonshine Base English. Microphone capture, WAV import, recognition, metrics,
and exports remain entirely on-device. The model ships inside the APK and the
app has no network permission.

## Build

Requirements: Android Studio/JDK 17 and Android SDK 36.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

The ARM64 debug APK is written to
`app/build/outputs/apk/debug/app-debug.apk`. Install it on an Android 12 or newer
ARM64 device.

## Record and benchmark

1. Tap **Record** and grant microphone access when Android asks.
2. Speak English, then tap **Stop**. Pressing Record again while capture is
   active or Stop while idle is safely ignored.
3. Moonshine transcribes after capture stops. The captured WAV remains in
   memory until another recording/import replaces it or **Clear** is tapped.
4. Tap **Retest** to run the retained, identical PCM audio through Moonshine
   again and obtain another inference measurement.

Capture uses 16,000 Hz, mono, signed 16-bit little-endian PCM. The exported WAV
contains the original PCM samples supplied by Android's `AudioRecord`; the
transcription path receives a float conversion of those same samples. Long
audio is decoded sequentially in eight-second pieces to preserve the existing
known-good Sherpa Moonshine v2 path.

## Import a WAV

Tap **Import WAV** and choose a local file with Android's system file picker.
The app accepts RIFF/WAVE files containing uncompressed 16-bit PCM with one or
two channels and a sample rate from 8 kHz through 192 kHz. Stereo is downmixed
to mono and non-16 kHz audio is linearly resampled to 16 kHz before inference.
The selected source file is never modified. The normalized 16 kHz mono model
input is retained for **Retest** and is the WAV written by **Save**, making it a
reproducible input for later engine comparisons.

Compressed WAV, floating-point WAV, WAV with more than two channels, malformed
headers, unsupported sample rates, and files above the 128 MiB PCM-data import
limit produce a clear local error. FFmpeg is not included.

## Metrics and RTF

After each successful inference, the compact Benchmark area displays:

- audio duration in seconds;
- measured inference duration in seconds;
- Real-Time Factor (RTF);
- transcript word count;
- model name, backend, and device ABI.

Inference duration is measured with `SystemClock.elapsedRealtimeNanos()` around
Sherpa stream creation, feature ingestion, decoding, result retrieval, and
release for each chunk. It excludes model startup, file selection, WAV parsing,
chunk copying, UI work, and export I/O.

`RTF = inference time in seconds / audio duration in seconds`

- RTF below `1.0`: faster than real time.
- RTF equal to `1.0`: one second of processing per second of audio.
- RTF above `1.0`: slower than real time.

## Save a benchmark bundle

Tap **Save** after an inference. On the first save, Android asks you to select
the primary **Music** folder. The app persists that user-granted Storage Access
Framework access; no storage permission is requested.

Each save creates three UTF-8/PCM files with one shared timestamp in
`Music/Speech2Text/`:

```text
transcript_yyyy-MM-dd_HH-mm-ss.txt
transcript_yyyy-MM-dd_HH-mm-ss.wav
transcript_yyyy-MM-dd_HH-mm-ss.json
```

The JSON records the model/backend, audio duration, inference duration, RTF,
word count, sample rate, channels, device, ABI, Android version, app version,
and whether the audio came from the microphone or an imported WAV. If any part
of the three-file save fails, files created by that attempt are removed and a
save error is shown.

## Code structure

- `TranscriptionEngine` is the small ASR boundary.
- `MoonshineTranscriptionEngine` owns the existing Sherpa recognizer and
  eight-second decoding behavior.
- `WavCodec` reads, validates, normalizes, and writes PCM WAV without an
  external media dependency.
- `BenchmarkData` calculates RTF/word count and serializes metadata without an
  external JSON library.
- `TranscriptionViewModel` coordinates capture, background inference, SAF I/O,
  retained audio, and UI state.

See [MODEL_FILES.md](MODEL_FILES.md) for exact bundled model files and checksums.

## Privacy and platform scope

The only user-visible runtime permission is `android.permission.RECORD_AUDIO`.
The merged APK manifest has no `INTERNET` or storage permission. There is no
cloud API, analytics, crash reporting, telemetry, authentication, database,
background service, realtime streaming, hotword support, or second ASR model.
Capture is stopped when the activity is no longer visible.
