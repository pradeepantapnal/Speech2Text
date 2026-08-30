# Moonshine Base model files

The APK bundles the English Moonshine v2 Base quantized model from the official
Sherpa-ONNX `asr-models` release. No files are downloaded at runtime.

Source archive:

`https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-moonshine-base-en-quantized-2026-02-27.tar.bz2`

Place these three runtime files under
`app/src/main/assets/sherpa-onnx-moonshine-base-en-quantized-2026-02-27/`:

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `encoder_model.ort` | 31,326,816 | `7c66495948d0d08ec1af454cd4b5514862ae6511e94712a60e6d83eaec8dc8cf` |
| `decoder_model_merged.ort` | 109,424,400 | `d9d7b333af34bc552580576ddcf248a1c6c839e0d3b43b09afb9376ed009899d` |
| `tokens.txt` | 549,350 | `2870d843e14c1e187bf1913a521562a63b53933814bd7f2145120468f494a049` |

`LICENSE` from the model archive is bundled beside them for attribution but is
not read by the app. Do not substitute the older Moonshine v1 files named
`preprocess.onnx`, `encode.int8.onnx`, `uncached_decode.int8.onnx`, and
`cached_decode.int8.onnx`; they use a different Sherpa configuration.

The native runtime is the official `sherpa-onnx-1.13.4.aar`, stored in
`app/libs/`, with SHA-256
`03f9c4df965f21c71269365a7951a7f23b5696fddd093fa318c80d65550ab780`.
Gradle filters the AAR to `arm64-v8a` when packaging the APK.

The current Sherpa-exported Moonshine v2 merged decoder has a known failure for
audio inputs of ten seconds or longer. The app therefore splits captured audio
into eight-second pieces before passing each piece to `OfflineRecognizer`.
