# Speech2Text V0.4

Android 12+ ARM64 transcription and benchmarking with Sherpa-ONNX, Moonshine
Base English, and an offline English Zipformer transducer. Microphone capture,
WAV import, recognition, same-WAV comparison, metrics, and exports remain
entirely on-device. All models ship inside the APK and the app has no network
permission.

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
3. The selected engine transcribes after capture stops. The captured WAV remains in
   memory until another recording/import replaces it or **Clear** is tapped.
4. Tap **Retest** to run the retained, identical PCM audio through the
   currently selected engine again and obtain another inference measurement.

The primary screen keeps only Record/Stop, Save, and **Advanced**. Import WAV,
Clear, engine selection, technical hotwords, Compare, Retest, benchmark details,
and About are grouped inside the Advanced bottom sheet.

## Engine comparison

Use the compact **Engine** selector to choose **Moonshine Base** or
**Zipformer / Transducer**. The selection is remembered locally. Zipformer uses
`modified_beam_search` with the built-in technical hotword list enabled by
default. The **Technical hotwords** switch disables or enables decoder-level
contextual biasing; it is not post-processing. The conservative hotword score
is `1.5`, matching sherpa-onnx's documented default.

After recording or importing a WAV, tap **Compare** to run the same normalized
16 kHz mono PCM sequentially through both engines. The comparison shows each
transcript, inference time, RTF, word count, and Zipformer hotword state. If
Music folder access is already granted, it also writes
`comparison_yyyy-MM-dd_HH-mm-ss.json` under `Music/Speech2Text/`.

The selected second model is the official
`sherpa-onnx-zipformer-small-en-2023-06-26` English model from the
[sherpa-onnx offline transducer model documentation](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-transducer/zipformer-transducer-models.html)
and its [official GitHub release](https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-zipformer-small-en-2023-06-26.tar.bz2).
The bundled files are `encoder-epoch-99-avg-1.int8.onnx` (about 25 MiB),
`decoder-epoch-99-avg-1.onnx` (about 2.0 MiB),
`joiner-epoch-99-avg-1.onnx` (about 1.0 MiB), `tokens.txt`, and
`technical_hotwords.txt`. The int8 encoder keeps the mobile footprint near
28 MiB while retaining the official float decoder and joiner.

For repeatable evaluation, use [benchmark/technical_benchmark.txt](benchmark/technical_benchmark.txt),
which combines ordinary English sentences with UEFI, EDK2, DDR5, LPCAMM2,
SELinux, AIDL, HIDL, Perfetto, Android Automotive, Qualcomm, MediaTek, Yocto,
UFS, AVB, and other technical terms. Record or import that script as one WAV,
run **Compare**, and compare inference time, RTF, word count, and transcripts.

While recording, the screen stays awake and a live waveform shows the smoothed
RMS amplitude of the microphone PCM samples. Recording is limited to 60:00;
at that point capture stops automatically, the WAV is finalized, and normal
offline transcription continues. The screen-awake flag is removed as soon as
recording ends.

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
