# LocalMathy

Fully offline, on-device math solving for Android (iOS planned). LocalMathy
runs [VibeThinker-3B](https://huggingface.co/litert-community/VibeThinker-3B) on
[LiteRT](https://ai.google.dev/edge/litert) — ask a math question (or snap a
photo of one) and it reasons through the problem and renders the answer, all
without a network connection or an account. Nothing you type or photograph
leaves the device.

## Screenshots

| Ask a question | Worked solution | Solve from a photo |
| :---: | :---: | :---: |
| <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="230"> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="230"> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="230"> |

## Features

- **Fully offline and private** — all inference runs locally; no account, no
  cloud, no telemetry.
- **Step-by-step reasoning** streamed into a collapsible "Thinking" pane, then a
  clean final answer.
- **Markdown + LaTeX** answer rendering, with tap-to-copy on boxed answers.
- **Solve from a photo** — snap or pick a picture of a problem, crop it, and an
  optional on-device vision model reads it into an editable question.
- **Optional local history** of questions and answers, stored on-device.
- **Light and dark themes**.

## How it works

- Built with **Kotlin Multiplatform + Compose Multiplatform**. All UI and app
  logic live in `composeApp/src/commonMain`; platform pieces (inference,
  model storage, markdown rendering) sit behind small interfaces with Android
  implementations in `composeApp/src/androidMain`. iOS is stubbed for now.
- Inference runs through the **LiteRT-LM runtime**
  (`com.google.ai.edge.litertlm`) with the **GPU backend** (CPU fallback)
  and a 4096-token budget — the same setup as
  `litert-lm run model.litertlm --backend=gpu --max-num-tokens 4096`.
- VibeThinker is a **single-turn reasoning model**: every question runs in a
  fresh session, and follow-up questions are not possible. The app streams the
  `<think>…</think>` chain of thought into a collapsible "Thinking" pane, then
  renders the final answer.
- Answers are rendered as **Markdown + LaTeX** in a WebView using bundled
  (fully offline) copies of [marked](https://github.com/markedjs/marked) and
  [KaTeX](https://katex.org) (`composeApp/src/androidMain/assets/render/`).
- Photo solving uses an optional **Gemma 4 E2B** vision model, run on-device
  only to transcribe a cropped photo into text that the solver then answers.

## Getting the model

On first launch the app offers two options for the reasoning model:

1. **Download from Hugging Face** — fetches `model.litertlm` (~1.9 GB, int4)
   straight from `litert-community/VibeThinker-3B`. The download is resumable.
2. **Import a `.litertlm` file** — if you already have the model on the device
   (e.g. from termux), pick it with the system file picker and the app copies
   it into its own storage.

The model is stored in the app's internal storage
(`files/models/VibeThinker-3B.litertlm`) — internal storage is plain ext4,
which the GPU backend needs to mmap the weights quickly; the shared/external
storage layer (FUSE) stalls GPU loading.

Photo solving needs a separate optional vision model (Gemma 4 E2B, ~2.6 GB),
which you can download or import the first time you use the feature. Typed
questions don't need it.

## Building

```sh
./gradlew :composeApp:assembleDebug
```

Requires JDK 17+ and the Android SDK (compileSdk 35). Install the debug APK
with `./gradlew :composeApp:installDebug` or via Android Studio.

## F-Droid

Store listing metadata (title, descriptions, changelogs, and screenshots)
lives under `fastlane/metadata/android/en-US/`, following the
[fastlane structure](https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/)
that F-Droid reads directly from the repository.

## License

LocalMathy is licensed under the **GNU AGPL-3.0** — see [LICENSE](LICENSE).
